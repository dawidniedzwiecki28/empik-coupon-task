package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpDatabase
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpTestFixtures
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
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

	private val userId = UserId(UUID.randomUUID())
	private val clientIp = IpAddress.of("1.1.1.1")

	// --- creation ---

	@Test
	fun `creates a coupon from the command and returns its id`() {
		// given
		whenever(couponRepository.saveAndFlush(any())).thenAnswer { it.getArgument<CouponEntity>(0) }

		// when
		val id = operations.createCoupon(
			CreateCouponCommand(code = CouponCode.of("  wiosna  "), maxUses = 3, country = CountryCode.of("pl")),
		)

		// then
		val captor = argumentCaptor<CouponEntity>()
		verify(couponRepository).saveAndFlush(captor.capture())
		val saved = captor.firstValue
		assertEquals("WIOSNA", saved.code)
		assertEquals("PL", saved.country)
		assertEquals(3, saved.maxUses)
		assertEquals(0, saved.currentUses)
		assertEquals(Instant.parse("2026-01-01T00:00:00Z"), saved.createdAt)
		assertEquals(saved.id, id.value)
	}

	@Test
	fun `rejects non-positive maxUses`() {
		// expect
		assertFailsWith<IllegalArgumentException> {
			operations.createCoupon(CreateCouponCommand(code = CouponCode.of("X"), maxUses = 0, country = CountryCode.of("PL")))
		}
	}

	@Test
	fun `translates a unique-code violation to CouponCodeAlreadyExistsException`() {
		// given
		whenever(couponRepository.saveAndFlush(any()))
			.thenThrow(dataIntegrityViolation(CouponEntity.UNIQUE_CODE_CONSTRAINT))

		// expect
		assertFailsWith<CouponCodeAlreadyExistsException> {
			operations.createCoupon(CreateCouponCommand(code = CouponCode.of("SUMMER"), maxUses = 1, country = CountryCode.of("PL")))
		}
	}

	@Test
	fun `rethrows integrity violations that are not the unique-code constraint`() {
		// given — a different constraint (e.g. a check) must not be mistaken for a duplicate code
		whenever(couponRepository.saveAndFlush(any()))
			.thenThrow(dataIntegrityViolation("ck_coupons_max_uses_positive"))

		// expect
		assertFailsWith<DataIntegrityViolationException> {
			operations.createCoupon(CreateCouponCommand(code = CouponCode.of("SUMMER"), maxUses = 1, country = CountryCode.of("PL")))
		}
	}

	// --- redemption ---

	@Test
	fun `redeem returns CouponNotFound for unknown code`() {
		// given
		whenever(couponRepository.findByCode("NOPE")).thenReturn(null)

		// when
		val result = operations.redeem(RedeemCouponCommand(CouponCode.of("NOPE"), userId, clientIp))

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
		val result = operations.redeem(RedeemCouponCommand(CouponCode.of("WIOSNA"), userId, clientIp))

		// then
		val notAllowed = assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals("PL", notAllowed.requiredCountry)
		assertEquals("DE", notAllowed.callerCountry)
		verify(redemptionExecutor, never()).consume(any(), any())
	}

	@Test
	fun `redeem looks up the normalized code and returns the executor result`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.country = CountryCode.of("PL")
		whenever(redemptionExecutor.consume(any(), any())).thenReturn(RedemptionResult.Success)

		// when
		val result = operations.redeem(RedeemCouponCommand(CouponCode.of("wiosna"), userId, clientIp))

		// then
		assertEquals(RedemptionResult.Success, result)
	}

	@Test
	fun `redeem propagates geo-IP failure (fail-closed) and never touches the store`() {
		// given
		whenever(couponRepository.findByCode("WIOSNA")).thenReturn(coupon(country = "PL"))
		geoIp.failure = GeoIpUnavailableException("1.1.1.1")

		// when
		assertFailsWith<GeoIpUnavailableException> {
			operations.redeem(RedeemCouponCommand(CouponCode.of("WIOSNA"), userId, clientIp))
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

	/** Mirrors how Spring wraps a Hibernate constraint failure: a DIV whose cause carries the constraint name. */
	private fun dataIntegrityViolation(constraint: String) =
		DataIntegrityViolationException(
			"boom",
			ConstraintViolationException("boom", SQLException("boom"), ConstraintViolationException.ConstraintKind.OTHER, constraint),
		)
}

/** No interface to mock, so the double subclasses the resolver; the bundled database is never consulted. */
private class FakeGeoIpResolver : GeoIpResolver(SHARED_DATABASE) {
	var country: CountryCode = CountryCode.of("PL")
	var failure: GeoIpUnavailableException? = null

	override fun resolveCountry(ip: IpAddress): CountryCode {
		failure?.let { throw it }
		return country
	}

	private companion object {
		val SHARED_DATABASE = GeoIpDatabase(GeoIpTestFixtures.bundledReader())
	}
}
