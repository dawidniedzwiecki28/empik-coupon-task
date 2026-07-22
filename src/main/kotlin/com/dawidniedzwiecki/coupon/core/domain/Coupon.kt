package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import java.time.Instant
import java.util.UUID

/** A discount coupon; [code] is always the normalized (trim + upper-case) form. */
data class Coupon(
	val id: UUID,
	val code: String,
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	val country: CountryCode,
) {
	companion object {
		fun normalizeCode(raw: String): String = raw.trim().uppercase()
	}
}
