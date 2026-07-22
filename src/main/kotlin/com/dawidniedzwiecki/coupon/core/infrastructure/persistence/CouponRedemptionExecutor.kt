package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The redemption write in one short transaction; the geo-IP call runs before it, so no row lock spans a network call. */
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
				// Insert first: a repeat user is rejected before the counter changes. Both failure
				// branches throw, so the transaction rolls back — nothing persists unless it succeeds.
				redemptionRepository.insertRedemption(couponId, userId, Instant.now(clock))
				if (couponRepository.incrementUsesIfBelowMax(couponId) == 0) {
					throw LimitReachedException()
				}
				// Just incremented under lock, so the row must exist; a miss means data corruption.
				val coupon = couponRepository.findByIdOrNull(couponId)
					?: error("Coupon $couponId not found immediately after a successful increment")
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
