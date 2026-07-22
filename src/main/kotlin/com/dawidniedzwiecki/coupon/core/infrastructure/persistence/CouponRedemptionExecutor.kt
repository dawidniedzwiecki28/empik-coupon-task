package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The redemption write in one short transaction; the geo-IP call runs before it, so no row lock spans a network call. */
@Service
class CouponRedemptionExecutor(
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
	private val clock: Clock,
) {

	@Transactional
	fun consume(couponId: UUID, userId: UUID): ConsumeOutcome {
		// Insert-first (ON CONFLICT DO NOTHING): a repeat user is rejected before the counter changes,
		// and it reports no rows rather than throwing.
		if (redemptionRepository.insertIfAbsent(couponId, userId, Instant.now(clock)) == 0) {
			return ConsumeOutcome.AlreadyRedeemed
		}
		if (couponRepository.incrementUsesIfBelowMax(couponId) == 0) {
			// Coupon is full — undo the tentative redemption within the same transaction.
			redemptionRepository.deleteRedemption(couponId, userId)
			return ConsumeOutcome.LimitReached
		}
		return ConsumeOutcome.Redeemed
	}
}

/** Result of a single [CouponRedemptionExecutor.consume] attempt. */
sealed interface ConsumeOutcome {
	data object Redeemed : ConsumeOutcome

	data object LimitReached : ConsumeOutcome

	data object AlreadyRedeemed : ConsumeOutcome
}
