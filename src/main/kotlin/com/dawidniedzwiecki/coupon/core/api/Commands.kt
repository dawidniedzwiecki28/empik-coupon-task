package com.dawidniedzwiecki.coupon.core.api

data class CreateCouponCommand(
	val code: String,
	val maxUses: Int,
	val country: String,
)

/** [clientIp] is resolved at the REST edge and passed in, keeping the core free of the servlet layer. */
data class RedeemCouponCommand(
	val code: String,
	val userId: String,
	val clientIp: String,
)
