package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface CouponRedemptionRepository : JpaRepository<CouponRedemptionEntity, CouponRedemptionId> {

	/** Insert-if-absent: returns 1 when inserted, 0 when this (coupon_id, user_id) already exists — no exception. */
	@Modifying
	@Query(
		value = "INSERT INTO coupon_redemptions (coupon_id, user_id, redeemed_at) VALUES (:couponId, :userId, :redeemedAt) " +
			"ON CONFLICT (coupon_id, user_id) DO NOTHING",
		nativeQuery = true,
	)
	fun insertIfAbsent(couponId: UUID, userId: UUID, redeemedAt: Instant): Int

	@Modifying
	@Query(
		value = "DELETE FROM coupon_redemptions WHERE coupon_id = :couponId AND user_id = :userId",
		nativeQuery = true,
	)
	fun deleteRedemption(couponId: UUID, userId: UUID)

	/** Scoped per-coupon redemption count (avoids whole-table counts). */
	fun countByIdCouponId(couponId: UUID): Long
}
