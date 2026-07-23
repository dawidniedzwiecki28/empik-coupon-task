package com.dawidniedzwiecki.coupon.rest.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.util.UUID

data class CreateCouponRequest(
	@field:NotBlank val code: String,
	@field:Positive val maxUses: Int,
	@field:NotBlank val country: String,
)

data class CreateCouponResponse(val couponId: UUID)

data class RedeemCouponRequest(
	@field:NotBlank val code: String,
	val userId: UUID,
)
