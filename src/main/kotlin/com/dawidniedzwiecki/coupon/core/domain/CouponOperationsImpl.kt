package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CouponView
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.ConsumeOutcome
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionExecutor
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import java.time.Clock

/** Stateless; pushes concurrency invariants down to [CouponRedemptionExecutor], so it scales horizontally. */
class CouponOperationsImpl(
	private val couponRepository: CouponRepository,
	private val redemptionExecutor: CouponRedemptionExecutor,
	private val geoIpResolver: GeoIpResolver,
	private val clock: Clock,
) : CouponOperations {

	private val log = LoggerFactory.getLogger(javaClass)

	override fun createCoupon(command: CreateCouponCommand): CouponView {
		val entity = CouponEntity.create(command, clock)
		val saved = try {
			couponRepository.saveAndFlush(entity)
		} catch (_: DataIntegrityViolationException) {
			throw CouponCodeAlreadyExistsException(entity.code)
		}
		log.info("Coupon created: code={} country={} maxUses={}", saved.code, saved.country, saved.maxUses)
		return saved.toView()
	}

	override fun redeem(command: RedeemCouponCommand): RedemptionResult {
		val normalizedCode = CouponEntity.normalizeCode(command.code)
		val coupon = couponRepository.findByCode(normalizedCode)
		if (coupon == null) {
			log.info("Redemption rejected: outcome=COUPON_NOT_FOUND code={} userId={}", normalizedCode, command.userId)
			return RedemptionResult.CouponNotFound
		}

		// Resolve country before the write transaction so the external call never holds a row lock;
		// fail-closed on error.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		val couponCountry = CountryCode.of(coupon.country)
		if (couponCountry != callerCountry) {
			log.info(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId, couponCountry, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(couponCountry.value, callerCountry.value)
		}

		return when (redemptionExecutor.consume(coupon.id, command.userId)) {
			ConsumeOutcome.Redeemed -> {
				log.info(
					"Redemption succeeded: code={} userId={} country={}",
					coupon.code, command.userId, callerCountry,
				)
				RedemptionResult.Success(coupon.code, callerCountry.value)
			}

			ConsumeOutcome.LimitReached -> {
				log.info("Redemption rejected: outcome=LIMIT_REACHED code={} userId={}", coupon.code, command.userId)
				RedemptionResult.LimitReached
			}

			ConsumeOutcome.AlreadyRedeemed -> {
				log.info("Redemption rejected: outcome=ALREADY_REDEEMED code={} userId={}", coupon.code, command.userId)
				RedemptionResult.AlreadyRedeemedByUser
			}
		}
	}
}

private fun CouponEntity.toView(): CouponView =
	CouponView(id, code, createdAt, maxUses, currentUses, country)
