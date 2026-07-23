package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.FakeGeoIpConfig
import com.dawidniedzwiecki.coupon.FakeGeoIpResolver
import com.dawidniedzwiecki.coupon.TestcontainersConfiguration
import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** The primary test: full create/redeem behaviour against a real PostgreSQL, with geo-IP faked. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, FakeGeoIpConfig::class)
class CouponOperationsTest @Autowired constructor(
	private val operations: CouponOperations,
	private val coupons: CouponRepository,
	private val redemptions: CouponRedemptionRepository,
	private val geoIp: FakeGeoIpResolver,
) {

	@BeforeEach
	fun reset() {
		redemptions.deleteAll()
		coupons.deleteAll()
		geoIp.reset()
	}

	// --- creation ---

	@Test
	fun `creates a coupon, normalizing the code and starting the counter at zero`() {
		// when
		val id = create(code = "  wiosna  ", maxUses = 3, country = "pl")

		// then
		val stored = coupons.findById(id.value).get()
		assertEquals("WIOSNA", stored.code)
		assertEquals("PL", stored.country)
		assertEquals(3, stored.maxUses)
		assertEquals(0, stored.currentUses)
	}

	@Test
	fun `rejects a duplicate code case-insensitively`() {
		// given
		create(code = "SUMMER")

		// expect
		assertFailsWith<CouponCodeAlreadyExistsException> { create(code = "summer") }
	}

	@Test
	fun `rejects a non-positive maxUses`() {
		// expect
		assertFailsWith<IllegalArgumentException> { create(code = "ZERO", maxUses = 0) }
	}

	// --- redemption ---

	@Test
	fun `redeems a coupon, incrementing usage and recording the redemption`() {
		// given
		val id = create(code = "WIOSNA")

		// when
		val result = redeem(code = "WIOSNA")

		// then
		assertEquals(RedemptionResult.Success, result)
		assertEquals(1, coupons.findById(id.value).get().currentUses)
		assertEquals(1L, redemptions.countByIdCouponId(id.value))
	}

	@Test
	fun `rejects an unknown code`() {
		// expect
		assertEquals(RedemptionResult.CouponNotFound, redeem(code = "NOPE"))
	}

	@Test
	fun `rejects a caller from a different country without recording a redemption`() {
		// given
		val id = create(code = "WIOSNA", country = "PL")
		geoIp.country = CountryCode.of("DE")

		// when
		val result = redeem(code = "WIOSNA")

		// then
		val rejected = assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals("PL", rejected.requiredCountry)
		assertEquals("DE", rejected.callerCountry)
		assertEquals(0, coupons.findById(id.value).get().currentUses)
		assertEquals(0L, redemptions.countByIdCouponId(id.value))
	}

	@Test
	fun `rejects a second redemption by the same user`() {
		// given
		val id = create(code = "ONCE", maxUses = 5)
		val user = UUID.randomUUID()

		// when
		val first = redeem(code = "ONCE", user = user)
		val second = redeem(code = "ONCE", user = user)

		// then
		assertEquals(RedemptionResult.Success, first)
		assertEquals(RedemptionResult.AlreadyRedeemedByUser, second)
		assertEquals(1, coupons.findById(id.value).get().currentUses)
	}

	@Test
	fun `rejects a redemption once the usage limit is reached`() {
		// given
		create(code = "SINGLE", maxUses = 1)

		// when
		val first = redeem(code = "SINGLE")
		val second = redeem(code = "SINGLE")

		// then
		assertEquals(RedemptionResult.Success, first)
		assertEquals(RedemptionResult.LimitReached, second)
	}

	@Test
	fun `fails closed and records nothing when the caller country cannot be resolved`() {
		// given
		val id = create(code = "WIOSNA")
		geoIp.available = false

		// when
		assertFailsWith<GeoIpUnavailableException> { redeem(code = "WIOSNA") }

		// then — fail-closed: nothing is written
		assertEquals(0, coupons.findById(id.value).get().currentUses)
		assertEquals(0L, redemptions.countByIdCouponId(id.value))
	}

	@Test
	fun `lets exactly maxUses redemptions through under concurrent load`() {
		// given
		val maxUses = 20
		val attempts = 100
		val id = create(code = "RUSH", maxUses = maxUses)
		val pool = Executors.newFixedThreadPool(16)

		// when — distinct users race for the slots
		val outcomes = try {
			(1..attempts)
				.map { pool.submit(Callable { redeem(code = "RUSH") }) }
				.map { it.get(30, TimeUnit.SECONDS) }
		} finally {
			pool.shutdown()
		}

		// then — exactly maxUses succeed, the rest hit the limit
		assertEquals(maxUses, outcomes.count { it == RedemptionResult.Success })
		assertEquals(attempts - maxUses, outcomes.count { it == RedemptionResult.LimitReached })
		assertEquals(maxUses, coupons.findById(id.value).get().currentUses)
		assertEquals(maxUses.toLong(), redemptions.countByIdCouponId(id.value))
	}

	private fun create(code: String, maxUses: Int = 5, country: String = "PL") =
		operations.createCoupon(CreateCouponCommand(CouponCode.of(code), maxUses, CountryCode.of(country)))

	private fun redeem(code: String, user: UUID = UUID.randomUUID()) =
		operations.redeem(RedeemCouponCommand(CouponCode.of(code), UserId(user), CALLER_IP))

	private companion object {
		val CALLER_IP: IpAddress = IpAddress.of("1.1.1.1")
	}
}
