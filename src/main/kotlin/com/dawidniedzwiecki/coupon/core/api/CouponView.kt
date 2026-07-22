package com.dawidniedzwiecki.coupon.core.api

import java.time.Instant
import java.util.UUID

data class CouponView(
	val id: UUID,
	val code: String,
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	val country: String,
)
