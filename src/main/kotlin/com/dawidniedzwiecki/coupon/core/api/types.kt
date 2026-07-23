package com.dawidniedzwiecki.coupon.core.api

import java.util.UUID

/** Two-letter country code, upper-cased — ISO 3166-1 alpha-2 shape, not checked against the assigned list. */
@JvmInline
value class CountryCode private constructor(val value: String) {
	companion object {
		fun of(raw: String): CountryCode {
			// Validate before uppercasing — else a 1-char input like "ß" expands to "SS" and passes.
			val trimmed = raw.trim()
			if (trimmed.length != 2 || !trimmed.all { it in 'A'..'Z' || it in 'a'..'z' }) {
				throw InvalidValueException("Invalid ISO 3166-1 alpha-2 country code: '$raw'")
			}
			return CountryCode(trimmed.uppercase())
		}
	}

	override fun toString(): String = value
}

/** A syntactically valid IPv4/IPv6 address — a syntactic guard, not full RFC validation. */
@JvmInline
value class IpAddress private constructor(val value: String) {
	companion object {
		fun of(raw: String): IpAddress {
			val trimmed = raw.trim()
			if (!isIpv4(trimmed) && !isIpv6(trimmed)) throw InvalidValueException("Invalid IP address: '$raw'")
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

/** Coupon code, trimmed and upper-cased so uniqueness is case-insensitive. */
@JvmInline
value class CouponCode private constructor(val value: String) {
	companion object {
		/** Matches the coupons.code column width; raising it needs a migration. */
		const val MAX_LENGTH = 64

		fun of(raw: String): CouponCode {
			val normalized = raw.trim().uppercase()
			if (normalized.isEmpty() || normalized.length > MAX_LENGTH) {
				throw InvalidValueException("Invalid coupon code: '$raw'")
			}
			return CouponCode(normalized)
		}
	}

	override fun toString(): String = value
}

/** Caller-supplied user identifier — a mandated UUID the service treats as an opaque identifier. */
@JvmInline
value class UserId(val value: UUID)

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

/** Expected redemption outcomes — returned, not thrown, so the compiler enforces exhaustive HTTP mapping. */
sealed interface RedemptionResult {
	data object Success : RedemptionResult

	data object CouponNotFound : RedemptionResult

	data object LimitReached : RedemptionResult

	data class CountryNotAllowed(val requiredCountry: String, val callerCountry: String) : RedemptionResult

	data object AlreadyRedeemedByUser : RedemptionResult
}

/** A request value was rejected at construction; distinct from a stray IllegalArgumentException so the edge maps only this to 400. */
class InvalidValueException(message: String) : IllegalArgumentException(message)

class CouponCodeAlreadyExistsException(val code: String) :
	RuntimeException("A coupon with code '$code' already exists")

/** Distinct from the CountryNotAllowed outcome: the country could not be determined at all. */
class GeoIpUnavailableException(val ip: String, cause: Throwable? = null) :
	RuntimeException("Unable to resolve country for IP '$ip'", cause)
