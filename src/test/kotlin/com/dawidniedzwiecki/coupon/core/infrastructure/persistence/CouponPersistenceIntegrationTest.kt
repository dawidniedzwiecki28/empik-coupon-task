package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.TestcontainersConfiguration
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.assertEquals
import kotlin.test.assertIs

@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CouponPersistenceIntegrationTest @Autowired constructor(
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
	private val executor: CouponRedemptionExecutor,
) {

	@BeforeEach
	fun clear() {
		redemptionRepository.deleteAll()
		couponRepository.deleteAll()
	}

	@Test
	fun `findByCode matches the stored normalized code exactly`() {
		// given
		seedCoupon(code = "WIOSNA", maxUses = 3)

		// then
		assertEquals("WIOSNA", couponRepository.findByCode("WIOSNA")?.code)
		assertEquals(null, couponRepository.findByCode("wiosna"))
	}

	@Test
	@Transactional
	fun `incrementUsesIfBelowMax returns 1 until the limit, then 0`() {
		// given
		val couponId = seedCoupon(code = "ONE", maxUses = 1)

		// expect
		assertEquals(1, couponRepository.incrementUsesIfBelowMax(couponId))
		assertEquals(0, couponRepository.incrementUsesIfBelowMax(couponId))
	}

	@Test
	fun `exactly maxUses redemptions succeed under parallel load`() {
		// given
		val maxUses = 20
		val attempts = 100
		val couponId = seedCoupon(code = "RUSH", maxUses = maxUses)
		val pool = Executors.newFixedThreadPool(16)

		// when
		val outcomes = (1..attempts)
			.map { pool.submit(Callable { executor.consume(couponId, UUID.randomUUID()) }) }
			.map { it.get() }
		pool.shutdown()

		// then
		assertEquals(maxUses, outcomes.count { it is ConsumeOutcome.Redeemed })
		assertEquals(attempts - maxUses, outcomes.count { it is ConsumeOutcome.LimitReached })
		assertEquals(maxUses, couponRepository.findById(couponId).get().currentUses)
		assertEquals(maxUses.toLong(), redemptionRepository.countByIdCouponId(couponId))
	}

	@Test
	fun `the same user cannot redeem the same coupon twice`() {
		// given
		val couponId = seedCoupon(code = "ONCE", maxUses = 5)
		val user = UUID.randomUUID()

		// when
		val first = executor.consume(couponId, user)
		val second = executor.consume(couponId, user)

		// then
		assertIs<ConsumeOutcome.Redeemed>(first)
		assertEquals(ConsumeOutcome.AlreadyRedeemed, second)
		assertEquals(1, couponRepository.findById(couponId).get().currentUses)
		assertEquals(1L, redemptionRepository.countByIdCouponId(couponId))
	}

	private fun seedCoupon(code: String, maxUses: Int): UUID {
		val id = UUID.randomUUID()
		couponRepository.save(CouponEntity(id, code, Instant.now(), maxUses, 0, "PL"))
		return id
	}
}
