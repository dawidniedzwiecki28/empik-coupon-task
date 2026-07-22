package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CouponRedemptionRepository : JpaRepository<CouponRedemptionEntity, CouponRedemptionId> {

	/** Explicit INSERT (not save(), which upserts an assigned-id entity) so a duplicate PK surfaces as DataIntegrityViolationException. */
	@Modifying
	@Query(
		value = "INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES (:couponId, :userId, :redeemedAt)",
		nativeQuery = true,
	)
	fun insertRedemption(couponId: UUID, userId: UUID, redeemedAt: Instant)

	/** Scoped per-coupon redemption count (avoids whole-table counts). */
	fun countByIdCouponId(couponId: UUID): Long
}
