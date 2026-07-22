package com.dawidniedzwiecki.coupon.core.api

/**
 * The coupon domain's public contract. Callers (e.g. the REST layer) depend only on this
 * interface; the implementation and all persistence/geo-IP details are hidden behind it.
 */
interface CouponOperations {

	/**
	 * Creates a coupon with a normalized, case-insensitively unique code.
	 *
	 * @throws CouponCodeAlreadyExistsException if the normalized code already exists.
	 */
	fun createCoupon(command: CreateCouponCommand): CouponView

	/**
	 * Attempts to redeem a coupon for a user. Returns the outcome; expected rejections are
	 * [RedemptionResult] cases, not exceptions.
	 *
	 * @throws GeoIpUnavailableException if the caller's country cannot be determined
	 *   (fail-closed: a country-restricted coupon is not redeemed when geo-IP is down).
	 */
	fun redeem(command: RedeemCouponCommand): RedemptionResult
}
