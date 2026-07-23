package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.slf4j.LoggerFactory
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
	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	fun consume(couponId: UUID, userId: UUID): RedemptionResult {
		// Insert-first (ON CONFLICT DO NOTHING): a repeat user is rejected before the counter changes,
		// and it reports no rows rather than throwing.
		if (redemptionRepository.insertIfAbsent(couponId, userId, Instant.now(clock)) == 0) {
			log.debug("Redemption rejected: outcome=ALREADY_REDEEMED couponId={} userId={}", couponId, userId)
			return RedemptionResult.AlreadyRedeemedByUser
		}
		if (couponRepository.incrementUsesIfBelowMax(couponId) == 0) {
			// Coupon is full — undo the tentative redemption within the same transaction.
			redemptionRepository.deleteRedemption(couponId, userId)
			log.debug("Redemption rejected: outcome=LIMIT_REACHED couponId={} userId={}", couponId, userId)
			return RedemptionResult.LimitReached
		}
		log.debug("Redemption succeeded: couponId={} userId={}", couponId, userId)
		return RedemptionResult.Success
	}
}
