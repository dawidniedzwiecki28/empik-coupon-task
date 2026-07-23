package com.dawidniedzwiecki.coupon.core.api

import com.google.common.net.InetAddresses
import java.time.Instant
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

/** A valid IPv4/IPv6 address literal, checked on construction so an invalid address can't reach the geo-IP lookup. */
@JvmInline
value class IpAddress private constructor(val value: String) {
	companion object {
		fun of(raw: String): IpAddress {
			val trimmed = raw.trim()
			if (!InetAddresses.isInetAddress(trimmed)) throw InvalidValueException("Invalid IP address: '$raw'")
			return IpAddress(trimmed)
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

/** Read model for a single coupon — a framework-free projection so the entity never leaks past core.api. */
data class CouponView(
	val id: UUID,
	val code: String,
	val country: String,
	val maxUses: Int,
	val currentUses: Int,
	val createdAt: Instant,
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

/** No coupon exists for the given id — used by the read endpoint, where absence is a standard REST 404. */
class CouponNotFoundException(val id: UUID) :
	RuntimeException("No coupon exists with id '$id'")

/** Distinct from the CountryNotAllowed outcome: the country could not be determined at all. */
class GeoIpUnavailableException(val ip: String, cause: Throwable? = null) :
	RuntimeException("Unable to resolve country for IP '$ip'", cause)
