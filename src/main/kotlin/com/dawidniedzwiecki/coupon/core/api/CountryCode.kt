package com.dawidniedzwiecki.coupon.core.api

/**
 * ISO 3166-1 alpha-2 country code, always stored upper-case.
 *
 * A value class rather than a raw [String] so an invalid country cannot exist in the
 * domain and equality is well-defined for the coupon country check.
 */
@JvmInline
value class CountryCode private constructor(val value: String) {

	companion object {
		fun of(raw: String): CountryCode {
			val normalized = raw.trim().uppercase()
			require(normalized.length == 2 && normalized.all { it in 'A'..'Z' }) {
				"Invalid ISO 3166-1 alpha-2 country code: '$raw'"
			}
			return CountryCode(normalized)
		}
	}

	override fun toString(): String = value
}
