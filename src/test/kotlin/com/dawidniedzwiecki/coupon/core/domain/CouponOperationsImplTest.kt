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

class CouponOperationsImplTest {

	private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
	private val couponRepository = FakeCouponRepository()
	private val redemptionRepository = FakeCouponRedemptionRepository()
	private val geoIp = FakeGeoIpResolver()
	private val operations = CouponOperationsImpl(couponRepository, redemptionRepository, geoIp, clock)

	// --- creation ---

	@Test
	fun `creates a coupon with a normalized code`() {
		// when
		val view = operations.createCoupon(CreateCouponCommand(code = "  wiosna  ", maxUses = 3, country = "pl"))

		// then
		assertEquals("WIOSNA", view.code)
		assertEquals("PL", view.country)
		assertEquals(3, view.maxUses)
		assertEquals(0, view.currentUses)
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), view.createdAt)
	}

	@Test
	fun `rejects non-positive maxUses`() {
		// expect
		assertFailsWith<IllegalArgumentException> {
			operations.createCoupon(CreateCouponCommand(code = "X", maxUses = 0, country = "PL"))
		}
	}

	@Test
	fun `propagates duplicate code`() {
		// given
		operations.createCoupon(CreateCouponCommand(code = "SUMMER", maxUses = 1, country = "PL"))

		// expect
		assertFailsWith<CouponCodeAlreadyExistsException> {
			operations.createCoupon(CreateCouponCommand(code = "summer", maxUses = 1, country = "PL"))
		}
	}

	// --- redemption outcomes ---

	@Test
	fun `redeem returns CouponNotFound for unknown code`() {
		// expect
		assertEquals(
			RedemptionResult.CouponNotFound,
			operations.redeem(RedeemCouponCommand(code = "NOPE", userId = "u1", clientIp = "1.1.1.1")),
		)
	}

	@Test
	fun `redeem returns CountryNotAllowed when caller country differs`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("DE")

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		// then
		val notAllowed = assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals("PL", notAllowed.requiredCountry)
		assertEquals("DE", notAllowed.callerCountry)
		assertEquals(0, redemptionRepository.consumeCalls, "country rejection must not consume a redemption")
	}

	@Test
	fun `redeem is case-insensitive on the code`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionRepository.outcome = ConsumeOutcome.Redeemed(currentUses = 1, maxUses = 3)

		// when
		val result = operations.redeem(RedeemCouponCommand(code = "wiosna", userId = "u1", clientIp = "1.1.1.1"))

		// then
		assertIs<RedemptionResult.Success>(result)
	}

	@Test
	fun `redeem success reports remaining uses`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionRepository.outcome = ConsumeOutcome.Redeemed(currentUses = 1, maxUses = 3)

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		// then
		val success = assertIs<RedemptionResult.Success>(result)
		assertEquals(2, success.remainingUses)
		assertEquals("PL", success.country)
	}

	@Test
	fun `redeem maps LimitReached`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionRepository.outcome = ConsumeOutcome.LimitReached

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		// then
		assertEquals(RedemptionResult.LimitReached, result)
	}

	@Test
	fun `redeem maps AlreadyRedeemed`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("PL")
		redemptionRepository.outcome = ConsumeOutcome.AlreadyRedeemed

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))

		// then
		assertEquals(RedemptionResult.AlreadyRedeemedByUser, result)
	}

	@Test
	fun `redeem propagates geo-IP failure (fail-closed) and never touches the store`() {
		// given
		givenCoupon(code = "WIOSNA", country = "PL")
		geoIp.failure = GeoIpUnavailableException("1.1.1.1")

		// then
		assertFailsWith<GeoIpUnavailableException> {
			operations.redeem(RedeemCouponCommand("WIOSNA", "u1", "1.1.1.1"))
		}
		assertEquals(0, redemptionRepository.consumeCalls)
	}

	private fun givenCoupon(code: String, country: String) {
		operations.createCoupon(CreateCouponCommand(code = code, maxUses = 3, country = country))
	}
}

private class FakeCouponRepository : CouponRepository {
	private val byCode = mutableMapOf<String, Coupon>()

	override fun save(coupon: Coupon): Coupon {
		if (byCode.containsKey(coupon.code)) throw CouponCodeAlreadyExistsException(coupon.code)
		byCode[coupon.code] = coupon
		return coupon
	}

	override fun findByCode(normalizedCode: String): Coupon? = byCode[normalizedCode]
}

private class FakeCouponRedemptionRepository : CouponRedemptionRepository {
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
