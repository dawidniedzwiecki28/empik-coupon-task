package com.dawidniedzwiecki.coupon.core.api

/** Request to create a coupon. Input validation happens at the REST edge. */
data class CreateCouponCommand(
	val code: String,
	val maxUses: Int,
	val country: String,
)

/**
 * Request to redeem a coupon.
 *
 * [clientIp] is resolved at the REST edge (from the request, or an explicit override for
 * testing) and passed in, so the core never depends on the servlet layer.
 */
data class RedeemCouponCommand(
	val code: String,
	val userId: String,
	val clientIp: String,
)
