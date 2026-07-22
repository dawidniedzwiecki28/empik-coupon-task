package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.core.domain.Coupon

/** Persistence port for coupon create + lookup. */
interface CouponRepository {
	/** @throws com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException on duplicate code. */
	fun save(coupon: Coupon): Coupon

	fun findByCode(normalizedCode: String): Coupon?
}
