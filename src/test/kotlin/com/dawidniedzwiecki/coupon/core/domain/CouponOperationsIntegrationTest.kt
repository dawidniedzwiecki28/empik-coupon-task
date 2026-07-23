package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.TestcontainersConfiguration
import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpTestFixtures
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Full redemption flow of CouponOperations against a real database, driving every outcome end to end. */
@SpringBootTest
@Import(TestcontainersConfiguration::class, CouponOperationsIntegrationTest.GeoIpTestConfig::class)
class CouponOperationsIntegrationTest @Autowired constructor(
	private val operations: CouponOperations,
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
	private val geoIp: FixedCountryGeoIpResolver,
) {

	@TestConfiguration
	class GeoIpTestConfig {
		// Replaces the database-backed resolver so the suite controls the caller's country directly.
		@Bean
		@Primary
		fun fixedCountryGeoIpResolver(): FixedCountryGeoIpResolver = FixedCountryGeoIpResolver()
	}

	@BeforeEach
	fun reset() {
		redemptionRepository.deleteAll()
		couponRepository.deleteAll()
		geoIp.country = CountryCode.of("PL")
	}

	@Test
	fun `creates a coupon and persists it`() {
		// when
		val id = operations.createCoupon(CreateCouponCommand(CouponCode.of("wiosna"), maxUses = 3, country = CountryCode.of("pl")))

		// then
		val stored = couponRepository.findById(id.value).get()
		assertEquals("WIOSNA", stored.code)
		assertEquals("PL", stored.country)
		assertEquals(3, stored.maxUses)
		assertEquals(0, stored.currentUses)
	}

	@Test
	fun `redeems a coupon and increments its usage`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3)

		// when
		val result = redeem("WIOSNA", UUID.randomUUID())

		// then
		assertEquals(RedemptionResult.Success, result)
		assertEquals(1, couponRepository.findByCode("WIOSNA")!!.currentUses)
	}

	@Test
	fun `returns CouponNotFound for an unknown code`() {
		// expect
		assertEquals(RedemptionResult.CouponNotFound, redeem("NOPE", UUID.randomUUID()))
	}

	@Test
	fun `returns CountryNotAllowed when the caller country differs`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3)
		geoIp.country = CountryCode.of("DE")

		// when
		val result = redeem("WIOSNA", UUID.randomUUID())

		// then — no usage recorded
		assertIs<RedemptionResult.CountryNotAllowed>(result)
		assertEquals(0, couponRepository.findByCode("WIOSNA")!!.currentUses)
	}

	@Test
	fun `rejects a second redemption by the same user`() {
		// given
		val couponId = createCoupon(code = "ONCE", maxUses = 5)
		val user = UUID.randomUUID()

		// when
		val first = redeem("ONCE", user)
		val second = redeem("ONCE", user)

		// then
		assertEquals(RedemptionResult.Success, first)
		assertEquals(RedemptionResult.AlreadyRedeemedByUser, second)
		assertEquals(1, couponRepository.findById(couponId).get().currentUses)
		assertEquals(1L, redemptionRepository.countByIdCouponId(couponId))
	}

	@Test
	fun `rejects a redemption once the usage limit is reached`() {
		// given
		createCoupon(code = "SINGLE", maxUses = 1)

		// when — two distinct users
		val first = redeem("SINGLE", UUID.randomUUID())
		val second = redeem("SINGLE", UUID.randomUUID())

		// then
		assertEquals(RedemptionResult.Success, first)
		assertEquals(RedemptionResult.LimitReached, second)
		assertEquals(1, couponRepository.findByCode("SINGLE")!!.currentUses)
	}

	@Test
	fun `exactly maxUses redemptions succeed under concurrent load`() {
		// given
		val maxUses = 20
		val attempts = 100
		val couponId = createCoupon(code = "RUSH", maxUses = maxUses)
		val pool = Executors.newFixedThreadPool(16)

		// when — distinct users race for the limited slots
		val outcomes = try {
			(1..attempts)
				.map { pool.submit(Callable { redeem("RUSH", UUID.randomUUID()) }) }
				.map { it.get(30, TimeUnit.SECONDS) }
		} finally {
			pool.shutdown()
		}

		// then — the atomic increment lets through exactly maxUses; the rest are rejected as full
		assertEquals(maxUses, outcomes.count { it == RedemptionResult.Success })
		assertEquals(attempts - maxUses, outcomes.count { it == RedemptionResult.LimitReached })
		assertEquals(maxUses, couponRepository.findById(couponId).get().currentUses)
		assertEquals(maxUses.toLong(), redemptionRepository.countByIdCouponId(couponId))
	}

	private fun createCoupon(code: String, maxUses: Int): UUID =
		operations.createCoupon(CreateCouponCommand(CouponCode.of(code), maxUses, CountryCode.of("PL"))).value

	private fun redeem(code: String, userId: UUID): RedemptionResult =
		operations.redeem(RedeemCouponCommand(CouponCode.of(code), UserId(userId), IpAddress.of("1.1.1.1")))
}

/** Resolves every caller to a single configurable country; subclasses the resolver as there is no interface. */
class FixedCountryGeoIpResolver : GeoIpResolver(GeoIpTestFixtures.dummyDatabase()) {
	@Volatile
	var country: CountryCode = CountryCode.of("PL")

	override fun resolveCountry(ip: IpAddress): CountryCode = country
}
