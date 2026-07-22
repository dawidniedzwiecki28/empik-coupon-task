package com.dawidniedzwiecki.coupon.core.api

/** ISO 3166-1 alpha-2 country code, normalized upper-case; invalid values cannot be constructed. */
@JvmInline
value class CountryCode private constructor(val value: String) {

	companion object {
		fun of(raw: String): CountryCode {
			// Validate before uppercasing: otherwise a 1-char input like "ß" expands to "SS"
			// and would slip past the two-letter check.
			val trimmed = raw.trim()
			require(trimmed.length == 2 && trimmed.all { it in 'A'..'Z' || it in 'a'..'z' }) {
				"Invalid ISO 3166-1 alpha-2 country code: '$raw'"
			}
			return CountryCode(trimmed.uppercase())
		}
	}

	override fun toString(): String = value
}
