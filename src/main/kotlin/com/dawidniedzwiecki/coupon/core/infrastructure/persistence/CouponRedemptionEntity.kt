package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coupon_redemptions")
class CouponRedemptionEntity(
	@EmbeddedId
	var id: CouponRedemptionId,
	var redeemedAt: Instant,
)

/** Composite natural key: one redemption per user per coupon. */
@Embeddable
data class CouponRedemptionId(
	var couponId: UUID,
	var userId: UUID,
) : Serializable
