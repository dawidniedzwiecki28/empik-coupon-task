package com.dawidniedzwiecki.coupon.core.api

/** The coupon domain's public contract; persistence and geo-IP details sit behind it. */
interface CouponOperations {

	/** @throws CouponCodeAlreadyExistsException if the normalized code already exists. */
	fun createCoupon(command: CreateCouponCommand): CouponId

	/**
	 * Expected rejections are [RedemptionResult] cases, not exceptions.
	 * @throws GeoIpUnavailableException if the caller's country cannot be determined (fail-closed).
	 */
	fun redeem(command: RedeemCouponCommand): RedemptionResult
}
