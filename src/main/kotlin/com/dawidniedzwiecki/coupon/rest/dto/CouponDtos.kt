package com.dawidniedzwiecki.coupon.rest.dto

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.Instant
import java.util.UUID

/** A sane upper bound on maxUses: rejects absurd values at the edge while staying well within the int column. */
const val MAX_USES_LIMIT = 1_000_000L

data class CreateCouponRequest(
	@field:NotBlank val code: String,
	@field:Positive @field:Max(MAX_USES_LIMIT) val maxUses: Int,
	@field:NotBlank val country: String,
)

data class CreateCouponResponse(val couponId: UUID)

/** Read-model response for GET /api/coupons/{id}. */
data class CouponResponse(
	val id: UUID,
	val code: String,
	val country: String,
	val maxUses: Int,
	val currentUses: Int,
	val createdAt: Instant,
)

data class RedeemCouponRequest(
	@field:NotBlank val code: String,
	val userId: UUID,
)
