package com.dawidniedzwiecki.coupon.core.api

/**
 * Expected redemption outcomes, modelled as an exhaustive sealed type (returned, not thrown)
 * so the REST layer maps each to an HTTP status and the compiler enforces total handling.
 */
sealed interface RedemptionResult {

	data class Success(
		val couponCode: String,
		val country: String,
		val remainingUses: Int,
	) : RedemptionResult

	data object CouponNotFound : RedemptionResult

	data object LimitReached : RedemptionResult

	data class CountryNotAllowed(
		val requiredCountry: String,
		val callerCountry: String,
	) : RedemptionResult

	data object AlreadyRedeemedByUser : RedemptionResult
}
