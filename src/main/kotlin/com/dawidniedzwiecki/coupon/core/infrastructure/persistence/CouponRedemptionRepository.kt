package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CouponRedemptionRepository : JpaRepository<CouponRedemptionEntity, CouponRedemptionId> {

	/**
	 * Explicit INSERT (not save()/merge, which would upsert an assigned-id entity): a duplicate
	 * (coupon_id, user_id) hits the primary key and surfaces as a DataIntegrityViolationException.
	 */
	@Modifying
	@Query(
		value = "INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES (:couponId, :userId, :redeemedAt)",
		nativeQuery = true,
	)
	fun insertRedemption(couponId: UUID, userId: UUID, redeemedAt: Instant)

	/** Scoped count of redemptions for one coupon (avoids whole-table counts in assertions/queries). */
	fun countByIdCouponId(couponId: UUID): Long
}
