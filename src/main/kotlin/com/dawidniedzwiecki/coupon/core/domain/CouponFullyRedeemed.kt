package com.dawidniedzwiecki.coupon.core.domain

/**
 * Emitted once when a redemption takes a coupon to its maximum uses. An integration seam
 * (analytics, notifications, a future Kafka publication) with no coupling to storage.
 */
data class CouponFullyRedeemed(
	val couponCode: String,
	val country: String,
)
