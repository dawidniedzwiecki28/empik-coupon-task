package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Runs the redemption write in one short transaction. The external geo-IP call happens before
 * this, so no row lock is ever held across it.
 */
@Service
class CouponRedemptionExecutor(
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
	transactionManager: PlatformTransactionManager,
	private val clock: Clock,
) {
	private val transaction = TransactionTemplate(transactionManager)

	fun consume(couponId: UUID, userId: UUID): ConsumeOutcome =
		try {
			lateinit var redeemed: ConsumeOutcome
			transaction.executeWithoutResult {
				// Insert first: a repeat user is rejected before the counter is touched, and the
				// lock order is always redemption-then-coupon. Both failure branches throw, so the
				// whole transaction rolls back and nothing is persisted unless redemption succeeds.
				redemptionRepository.insertRedemption(couponId, userId, Instant.now(clock))
				if (couponRepository.incrementUsesIfBelowMax(couponId) == 0) {
					throw LimitReachedException()
				}
				val coupon = couponRepository.findById(couponId).orElseThrow()
				redeemed = ConsumeOutcome.Redeemed(coupon.currentUses, coupon.maxUses)
			}
			redeemed
		} catch (_: DataIntegrityViolationException) {
			ConsumeOutcome.AlreadyRedeemed
		} catch (_: LimitReachedException) {
			ConsumeOutcome.LimitReached
		}
}

private class LimitReachedException : RuntimeException()

/** Result of a single [CouponRedemptionExecutor.consume] attempt. */
sealed interface ConsumeOutcome {
	data class Redeemed(val currentUses: Int, val maxUses: Int) : ConsumeOutcome

	data object LimitReached : ConsumeOutcome

	data object AlreadyRedeemed : ConsumeOutcome
}
