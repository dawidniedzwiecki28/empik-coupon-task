package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CouponRepository : JpaRepository<CouponEntity, UUID> {

	fun findByCode(code: String): CouponEntity?

	/**
	 * Atomic, concurrency-safe usage increment: succeeds (returns 1) only while the coupon is
	 * below its limit, so no two callers can push it past max_uses. Returns 0 when exhausted.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(
		value = "UPDATE coupons SET current_uses = current_uses + 1 WHERE id = :id AND current_uses < max_uses",
		nativeQuery = true,
	)
	fun incrementUsesIfBelowMax(id: UUID): Int
}
