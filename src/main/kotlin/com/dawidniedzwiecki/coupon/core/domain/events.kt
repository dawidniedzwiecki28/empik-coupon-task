package com.dawidniedzwiecki.coupon.core.domain

/**
 * Emitted once, when a redemption takes a coupon to its maximum uses.
 *
 * Published as an integration seam (analytics, notifications, ...). It is intentionally
 * decoupled from any storage lifecycle. In production this is where a Kafka publication
 * would hang off, without changing the domain.
 */
data class CouponFullyRedeemed(
	val couponCode: String,
	val country: String,
)
