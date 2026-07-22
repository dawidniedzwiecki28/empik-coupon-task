package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CouponView
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Stateless; pushes concurrency invariants down to [CouponRedemptionStore], so it scales horizontally. */
class CouponService(
	private val catalog: CouponCatalog,
	private val redemptionStore: CouponRedemptionStore,
	private val geoIpResolver: GeoIpResolver,
	private val eventPublisher: DomainEventPublisher,
	private val clock: Clock,
) : CouponOperations {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun createCoupon(command: CreateCouponCommand): CouponView {
		require(command.maxUses > 0) { "maxUses must be positive" }
		val coupon = Coupon(
			id = UUID.randomUUID(),
			code = Coupon.normalizeCode(command.code),
			createdAt = Instant.now(clock),
			maxUses = command.maxUses,
			currentUses = 0,
			country = CountryCode.of(command.country),
		)
		val saved = catalog.save(coupon)
		log.info("Coupon created: code={} country={} maxUses={}", saved.code, saved.country, saved.maxUses)
		return saved.toView()
	}

	override fun redeem(command: RedeemCouponCommand): RedemptionResult {
		val normalizedCode = Coupon.normalizeCode(command.code)
		val coupon = catalog.findByCode(normalizedCode)
		if (coupon == null) {
			log.info("Redemption rejected: outcome=COUPON_NOT_FOUND code={} userId={}", normalizedCode, command.userId)
			return RedemptionResult.CouponNotFound
		}

		// Resolve country before the write tx so the external call never holds a row lock; fail-closed on error.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		if (coupon.country != callerCountry) {
			log.info(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId, coupon.country, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(coupon.country.value, callerCountry.value)
		}

		return when (val outcome = redemptionStore.consume(coupon.id, command.userId)) {
			is ConsumeOutcome.Redeemed -> {
				if (outcome.currentUses == outcome.maxUses) {
					eventPublisher.publish(CouponFullyRedeemed(coupon.code, coupon.country.value))
				}
				log.info(
					"Redemption succeeded: code={} userId={} country={} uses={}/{}",
					coupon.code, command.userId, callerCountry, outcome.currentUses, outcome.maxUses,
				)
				RedemptionResult.Success(coupon.code, callerCountry.value, outcome.maxUses - outcome.currentUses)
			}

			ConsumeOutcome.LimitReached -> {
				log.info("Redemption rejected: outcome=LIMIT_REACHED code={} userId={}", coupon.code, command.userId)
				RedemptionResult.LimitReached
			}

			ConsumeOutcome.AlreadyRedeemed -> {
				log.info(
					"Redemption rejected: outcome=ALREADY_REDEEMED code={} userId={}",
					coupon.code, command.userId,
				)
				RedemptionResult.AlreadyRedeemedByUser
			}
		}
	}
}

private fun Coupon.toView(): CouponView =
	CouponView(id, code, createdAt, maxUses, currentUses, country.value)
