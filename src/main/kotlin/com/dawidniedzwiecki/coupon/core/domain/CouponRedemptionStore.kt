package com.dawidniedzwiecki.coupon.core.domain

import java.util.UUID

/**
 * Concurrency-critical redemption step: the per-user uniqueness check and the usage-limit
 * increment must be atomic and safe across instances, persisting nothing unless it succeeds.
 */
interface CouponRedemptionStore {
	fun consume(couponId: UUID, userId: String): ConsumeOutcome
}

/** Result of a single [CouponRedemptionStore.consume] attempt. */
sealed interface ConsumeOutcome {
	/** Redemption recorded; carries post-increment counters. */
	data class Redeemed(val currentUses: Int, val maxUses: Int) : ConsumeOutcome

	/** Coupon already at max uses; nothing persisted. */
	data object LimitReached : ConsumeOutcome

	/** User had already redeemed this coupon; nothing persisted. */
	data object AlreadyRedeemed : ConsumeOutcome
}
