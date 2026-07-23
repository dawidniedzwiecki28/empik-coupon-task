package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface CouponRepository : JpaRepository<CouponEntity, UUID> {

	fun findByCode(code: String): CouponEntity?

	/**
	 * Atomic and capped: returns 1 while below max_uses, 0 once it is reached.
	 *
	 * `clearAutomatically` because this native UPDATE bypasses the persistence context — evict any now-stale
	 * managed coupon rather than let a later read in the same transaction see the pre-increment count.
	 */
	@Modifying(clearAutomatically = true)
	@Query(
		value = "UPDATE coupons SET current_uses = current_uses + 1 WHERE id = :id AND current_uses < max_uses",
		nativeQuery = true,
	)
	fun incrementUsesIfBelowMax(id: UUID): Int
}
