package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.ConsumeOutcome
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionExecutor
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CouponOperationsImplTest {

	private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
	private val couponRepository = mock<CouponRepository>()
	private val redemptionExecutor = mock<CouponRedemptionExecutor>()
	private val geoIp = FakeGeoIpResolver()
	private val operations = CouponOperationsImpl(couponRepository, redemptionExecutor, geoIp, clock)

	private val userId: UUID = UUID.randomUUID()
	private val clientIp = IpAddress.of("1.1.1.1")

	// --- creation ---

	@Test
	fun `creates a coupon with a normalized code`() {
		// given
		whenever(couponRepository.saveAndFlush(any())).thenAnswer { it.getArgument<CouponEntity>(0) }

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
	fun `translates a duplicate code to CouponCodeAlreadyExistsException`() {
		// given
		whenever(couponRepository.saveAndFlush(any())).thenThrow(DataIntegrityViolationException("dup"))

		// expect
		assertFailsWith<CouponCodeAlreadyExistsException> {
			operations.createCoupon(CreateCouponCommand(code = "SUMMER", maxUses = 1, country = "PL"))
		}
	}

	// --- redemption outcomes ---

	@Test
	fun `redeem returns CouponNotFound for unknown code`() {
		// given
		whenever(couponRepository.findByCode("NOPE")).thenReturn(null)

		// when
		val result = operations.redeem(RedeemCouponCommand(code = "NOPE", userId = userId, clientIp = clientIp))

		// then
		assertEquals(RedemptionResult.CouponNotFound, result)
		verify(redemptionExecutor, never()).consume(any(), any())
	}

	@Test
	fun `redeem returns CountryNotAllowed when caller country differs`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("DE")

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", userId, clientIp))

		// then
		val notAllowed = assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals("PL", notAllowed.requiredCountry)
		assertEquals("DE", notAllowed.callerCountry)
		verify(redemptionExecutor, never()).consume(any(), any())
	}

	@Test
	fun `redeem is case-insensitive on the code`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("PL")
		whenever(redemptionExecutor.consume(any(), any())).thenReturn(ConsumeOutcome.Redeemed)

		// when
		val result = operations.redeem(RedeemCouponCommand(code = "wiosna", userId = userId, clientIp = clientIp))

		// then
		assertIs<RedemptionResult.Success>(result)
	}

	@Test
	fun `redeem returns Success`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("PL")
		whenever(redemptionExecutor.consume(any(), any())).thenReturn(ConsumeOutcome.Redeemed)

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", userId, clientIp))

		// then
		assertEquals(RedemptionResult.Success, result)
	}

	@Test
	fun `redeem maps LimitReached`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("PL")
		whenever(redemptionExecutor.consume(any(), any())).thenReturn(ConsumeOutcome.LimitReached)

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", userId, clientIp))

		// then
		assertEquals(RedemptionResult.LimitReached, result)
	}

	@Test
	fun `redeem maps AlreadyRedeemed`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("PL")
		whenever(redemptionExecutor.consume(any(), any())).thenReturn(ConsumeOutcome.AlreadyRedeemed)

		// when
		val result = operations.redeem(RedeemCouponCommand("WIOSNA", userId, clientIp))

		// then
		assertEquals(RedemptionResult.AlreadyRedeemedByUser, result)
	}

	@Test
	fun `redeem propagates geo-IP failure (fail-closed) and never touches the store`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.failure = GeoIpUnavailableException("1.1.1.1")

		// when
		assertFailsWith<GeoIpUnavailableException> {
			operations.redeem(RedeemCouponCommand("WIOSNA", userId, clientIp))
		}

		// then
		verify(redemptionExecutor, never()).consume(any(), any())
	}

	private fun coupon(country: String) =
		CouponEntity(
			id = UUID.randomUUID(),
			code = "WIOSNA",
			createdAt = Instant.now(clock),
			maxUses = 3,
			currentUses = 0,
			country = country,
		)
}

private class FakeGeoIpResolver : GeoIpResolver {
	var country: CountryCode = CountryCode.of("PL")
	var failure: GeoIpUnavailableException? = null

	override fun resolveCountry(ip: IpAddress): CountryCode {
		failure?.let { throw it }
		return country
	}
}
