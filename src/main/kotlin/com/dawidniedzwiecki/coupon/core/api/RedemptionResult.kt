package com.dawidniedzwiecki.coupon.core.api

/**
 * Outcome of a redemption attempt.
 *
 * These are all *expected* business outcomes, not errors, so they are modelled as an
 * exhaustive sealed hierarchy (returned) rather than thrown exceptions. The REST layer
 * maps each case to an HTTP status; the compiler guarantees every case is handled.
 */
sealed interface RedemptionResult {

	/** The coupon was redeemed for this user. */
	data class Success(
		val couponCode: String,
		val country: String,
		val remainingUses: Int,
	) : RedemptionResult

	/** No coupon exists for the supplied code. */
	data object CouponNotFound : RedemptionResult

	/** The coupon has already reached its maximum number of uses. */
	data object LimitReached : RedemptionResult

	/** The caller's country does not match the coupon's target country. */
	data class CountryNotAllowed(
		val requiredCountry: String,
		val callerCountry: String,
	) : RedemptionResult

	/** This user has already redeemed this coupon. */
	data object AlreadyRedeemedByUser : RedemptionResult
}
