package com.dawidniedzwiecki.coupon.core.api

/**
 * The coupon domain's public contract. Callers depend only on this; the implementation and
 * all persistence/geo-IP details sit behind it.
 */
interface CouponOperations {

	/**
	 * Creates a coupon and returns its id.
	 *
	 * @throws CouponCodeAlreadyExistsException if the normalized code already exists.
	 */
	fun createCoupon(command: CreateCouponCommand): CouponId

	/**
	 * Expected rejections are [RedemptionResult] cases, not exceptions.
	 *
	 * @throws GeoIpUnavailableException if the caller's country cannot be determined (fail-closed).
	 */
	fun redeem(command: RedeemCouponCommand): RedemptionResult
}
