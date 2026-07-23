package com.dawidniedzwiecki.coupon.core.api

import java.util.UUID

/** ISO 3166-1 alpha-2 country code, normalized upper-case; invalid values cannot be constructed. */
@JvmInline
value class CountryCode private constructor(val value: String) {
	companion object {
		fun of(raw: String): CountryCode {
			// Validate before uppercasing — else a 1-char input like "ß" expands to "SS" and passes.
			val trimmed = raw.trim()
			require(trimmed.length == 2 && trimmed.all { it in 'A'..'Z' || it in 'a'..'z' }) {
				"Invalid ISO 3166-1 alpha-2 country code: '$raw'"
			}
			return CountryCode(trimmed.uppercase())
		}
	}

	override fun toString(): String = value
}

/**
 * A syntactically valid IP address (IPv4 or IPv6), validated on construction so an invalid
 * address cannot reach the geo-IP resolver. This is a syntactic guard, not full RFC validation.
 */
@JvmInline
value class IpAddress private constructor(val value: String) {
	companion object {
		fun of(raw: String): IpAddress {
			val trimmed = raw.trim()
			require(isIpv4(trimmed) || isIpv6(trimmed)) { "Invalid IP address: '$raw'" }
			return IpAddress(trimmed)
		}

		private fun isIpv4(s: String): Boolean {
			val parts = s.split('.')
			return parts.size == 4 && parts.all { part ->
				val octet = part.toIntOrNull()
				octet != null && octet in 0..255 && part == octet.toString() // rejects leading zeros
			}
		}

		private fun isIpv6(raw: String): Boolean {
			val s = raw.substringBefore('%') // ignore an optional zone index
			val compressed = "::" in s
			if (compressed) {
				if (s.indexOf("::") != s.lastIndexOf("::")) return false // at most one "::"
				// A single leading/trailing ':' that is not part of "::" is malformed (":1::", "::1:").
				if (s.startsWith(':') && !s.startsWith("::")) return false
				if (s.endsWith(':') && !s.endsWith("::")) return false
			} else if (s.startsWith(':') || s.endsWith(':')) {
				return false
			}
			val groups = s.split(':').filter { it.isNotEmpty() }
			var count = 0
			for ((i, g) in groups.withIndex()) {
				if (i == groups.lastIndex && '.' in g) {
					if (!isIpv4(g)) return false
					count += 2 // an embedded IPv4 tail occupies two 16-bit groups
				} else {
					if (g.length !in 1..4 || g.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return false
					count += 1
				}
			}
			return if (compressed) count <= 7 else count == 8
		}
	}

	override fun toString(): String = value
}

/** A coupon code, normalized (trimmed + upper-case) so uniqueness and lookups are case-insensitive. */
@JvmInline
value class CouponCode private constructor(val value: String) {
	companion object {
		/** Matches the coupons.code column width; not a runtime knob (raising it needs a migration). */
		const val MAX_LENGTH = 64

		fun of(raw: String): CouponCode {
			val normalized = raw.trim().uppercase()
			require(normalized.isNotEmpty() && normalized.length <= MAX_LENGTH) { "Invalid coupon code: '$raw'" }
			return CouponCode(normalized)
		}
	}

	override fun toString(): String = value
}

/** Opaque, caller-supplied user identifier — a UUID we mandate, so it is non-PII. */
@JvmInline
value class UserId(val value: UUID)

/** Identifier of a coupon. */
@JvmInline
value class CouponId(val value: UUID)

data class CreateCouponCommand(
	val code: CouponCode,
	val maxUses: Int,
	val country: CountryCode,
)

/** [clientIp] is resolved at the REST edge and passed in, keeping the core free of the servlet layer. */
data class RedeemCouponCommand(
	val code: CouponCode,
	val userId: UserId,
	val clientIp: IpAddress,
)

/**
 * Expected redemption outcomes, modelled as an exhaustive sealed type (returned, not thrown)
 * so the REST layer maps each to an HTTP status and the compiler enforces total handling.
 */
sealed interface RedemptionResult {
	data object Success : RedemptionResult

	data object CouponNotFound : RedemptionResult

	data object LimitReached : RedemptionResult

	data class CountryNotAllowed(val requiredCountry: String, val callerCountry: String) : RedemptionResult

	data object AlreadyRedeemedByUser : RedemptionResult
}

/** Thrown when creating a coupon whose normalized code already exists. */
class CouponCodeAlreadyExistsException(val code: String) :
	RuntimeException("A coupon with code '$code' already exists")

/** Caller's country could not be resolved from their IP — distinct from the CountryNotAllowed outcome. */
class GeoIpUnavailableException(val ip: String, cause: Throwable? = null) :
	RuntimeException("Unable to resolve country for IP '$ip'", cause)
