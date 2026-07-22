package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CouponServiceTest {

	private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
	private val catalog = FakeCouponCatalog()
	private val redemptionStore = FakeRedemptionStore()
	private val geoIp = FakeGeoIpResolver()
	private val events = RecordingEventPublisher()
	private val service = CouponService(catalog, redemptionStore, geoIp, events, clock)

	// --- creation ---

	@Test
	fun `creates a coupon with a normalized code`() {
		val view = service.createCoupon(CreateCouponCommand(code = "  wiosna  ", maxUses = 3, country = "pl"))

		assertEquals("WIOSNA", view.code)
		assertEquals("PL", view.country)
		assertEquals(3, view.maxUses)
		assertEquals(0, view.currentUses)
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), view.createdAt)
	}

	@Test
	fun `rejects non-positive maxUses`() {
		assertFailsWith<IllegalArgumentException> {
			service.createCoupon(CreateCouponCommand(code = "X", maxUses = 0, country = "PL"))
		}
	}

	@Test
	fun `propagates duplicate code`() {
		service.createCoupon(CreateCouponCommand(code = "SUMMER", maxUses = 1, country = "PL"))
		assertFailsWith<CouponCodeAlreadyExistsException> {
			service.createCoupon(CreateCouponCommand(code = "summer", maxUses = 1, country = "PL"))
		}
	}

	// --- redemption outcomes ---

	@Test
	fun `redeem returns CouponNotFound for unknown code`() {
		val result = service.redeem(RedeemCouponCommand(code = "NOPE", userId = "u1", clientIp = "1.1.1.1"))
		assertEquals(RedemptionResult.CouponNotFound, result)
	}

	@Test
	fun `redeem returns CountryNotAllowed when caller country differs`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("DE")

		val result = service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		val notAllowed = assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals("PL", notAllowed.requiredCountry)
		assertEquals("DE", notAllowed.callerCountry)
	}

	@Test
	fun `redeem is case-insensitive on the code`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionStore.outcome = ConsumeOutcome.Redeemed(currentUses = 1, maxUses = 3)

		val result = service.redeem(RedeemCouponCommand(code = "wiosna", userId = "u1", clientIp = "1.1.1.1"))

		assertIs<RedemptionResult.Success>(result)
	}

	@Test
	fun `redeem success reports remaining uses and does not publish when not full`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionStore.outcome = ConsumeOutcome.Redeemed(currentUses = 1, maxUses = 3)

		val result = service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		val success = assertIs<RedemptionResult.Success>(result)
		assertEquals(2, success.remainingUses)
		assertEquals("PL", success.country)
		assertTrue(events.published.isEmpty())
	}

	@Test
	fun `redeem publishes CouponFullyRedeemed on the last use`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionStore.outcome = ConsumeOutcome.Redeemed(currentUses = 3, maxUses = 3)

		val result = service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		val success = assertIs<RedemptionResult.Success>(result)
		assertEquals(0, success.remainingUses)
		assertEquals(listOf<Any>(CouponFullyRedeemed("WIOSNA", "PL")), events.published)
	}

	@Test
	fun `redeem maps LimitReached`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionStore.outcome = ConsumeOutcome.LimitReached

		val result = service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		assertEquals(RedemptionResult.LimitReached, result)
		assertTrue(events.published.isEmpty())
	}

	@Test
	fun `redeem maps AlreadyRedeemed`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionStore.outcome = ConsumeOutcome.AlreadyRedeemed

		val result = service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		assertEquals(RedemptionResult.AlreadyRedeemedByUser, result)
	}

	@Test
	fun `redeem propagates geo-IP failure (fail-closed) and never touches the store`() {
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.failure = GeoIpUnavailableException("1.1.1.1")

		assertFailsWith<GeoIpUnavailableException> {
			service.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))
		}
		assertEquals(0, redemptionStore.consumeCalls)
	}

	private fun givenCoupon(code: String, country: String) {
		service.createCoupon(CreateCouponCommand(code = code, maxUses = 3, country = country))
	}
}

private class FakeCouponCatalog : CouponCatalog {
	private val byCode = mutableMapOf<String, Coupon>()

	override fun save(coupon: Coupon): Coupon {
		if (byCode.containsKey(coupon.code)) throw CouponCodeAlreadyExistsException(coupon.code)
		byCode[coupon.code] = coupon
		return coupon
	}

	override fun findByCode(normalizedCode: String): Coupon? = byCode[normalizedCode]
}

private class FakeRedemptionStore : CouponRedemptionStore {
	var outcome: ConsumeOutcome = ConsumeOutcome.Redeemed(currentUses = 1, maxUses = 3)
	var consumeCalls = 0

	override fun consume(couponId: UUID, userId: String): ConsumeOutcome {
		consumeCalls++
		return outcome
	}
}

private class FakeGeoIpResolver : GeoIpResolver {
	var country: CountryCode = CountryCode.of("PL")
	var failure: GeoIpUnavailableException? = null

	override fun resolveCountry(ip: String): CountryCode {
		failure?.let { throw it }
		return country
	}
}

private class RecordingEventPublisher : DomainEventPublisher {
	val published = mutableListOf<Any>()

	override fun publish(event: Any) {
		published += event
	}
}
