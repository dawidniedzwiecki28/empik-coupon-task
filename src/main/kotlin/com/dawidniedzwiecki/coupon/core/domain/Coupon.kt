package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import java.time.Instant
import java.util.UUID

/**
 * A discount coupon. [code] is always the normalized (trimmed, upper-case) form, which is
 * what makes uniqueness and lookups case-insensitive.
 */
data class Coupon(
	val id: UUID,
	val code: String,
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	val country: CountryCode,
) {
	companion object {
		/** Normalizes a raw code so casing and surrounding whitespace never matter. */
		fun normalizeCode(raw: String): String = raw.trim().uppercase()
	}
}
