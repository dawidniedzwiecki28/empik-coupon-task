package com.dawidniedzwiecki.coupon.core.api

import java.time.Instant
import java.util.UUID

/** Read model of a coupon returned across the API boundary. */
data class CouponView(
	val id: UUID,
	val code: String,
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	val country: String,
)
