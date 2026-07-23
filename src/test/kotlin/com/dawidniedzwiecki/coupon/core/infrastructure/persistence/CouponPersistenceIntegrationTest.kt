package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.TestcontainersConfiguration
import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Repository-level guarantees against a real database; full redemption flows live in CouponOperationsIntegrationTest. */
@SpringBootTest
@Import(TestcontainersConfiguration::class)
class CouponPersistenceIntegrationTest @Autowired constructor(
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
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

		// expect
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
	fun `a duplicate code fails on the uq_coupons_code constraint`() {
		// given
		seedCoupon(code = "DUP", maxUses = 1)

		// when — the real Postgres unique violation, surfaced through Hibernate
		val ex = assertFailsWith<DataIntegrityViolationException> {
			couponRepository.saveAndFlush(CouponEntity(UUID.randomUUID(), "DUP", Instant.now(), 1, 0, "PL"))
		}

		// then — the constraint name the app matches on must be the one the DB actually reports
		val constraint = (ex.cause as? ConstraintViolationException)?.constraintName
		assertTrue(CouponEntity.UNIQUE_CODE_CONSTRAINT.equals(constraint, ignoreCase = true), "was: $constraint")
	}

	private fun seedCoupon(code: String, maxUses: Int): UUID {
		val id = UUID.randomUUID()
		couponRepository.save(CouponEntity(id, code, Instant.now(), maxUses, 0, "PL"))
		return id
	}
}
