package com.dawidniedzwiecki.coupon.core.api

import java.time.Instant
import java.util.UUID

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

data class CreateCouponCommand(
	val code: String,
	val maxUses: Int,
	val country: String,
)

/** [clientIp] is resolved at the REST edge and passed in, keeping the core free of the servlet layer. */
data class RedeemCouponCommand(
	val code: String,
	val userId: String,
	val clientIp: String,
)

data class CouponView(
	val id: UUID,
	val code: String,
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	val country: String,
)

/**
 * Expected redemption outcomes, modelled as an exhaustive sealed type (returned, not thrown)
 * so the REST layer maps each to an HTTP status and the compiler enforces total handling.
 */
sealed interface RedemptionResult {
	data class Success(val couponCode: String, val country: String, val remainingUses: Int) : RedemptionResult

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
