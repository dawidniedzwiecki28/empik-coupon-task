package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/** Stateless; pushes concurrency invariants down to [CouponRedemptionExecutor], so it scales horizontally. */
class CouponOperationsImpl(
	private val couponRepository: CouponRepository,
	private val redemptionExecutor: CouponRedemptionExecutor,
	private val geoIpResolver: GeoIpResolver,
	private val clock: Clock,
) : CouponOperations {

	private val log = LoggerFactory.getLogger(javaClass)

	@Transactional
	override fun createCoupon(command: CreateCouponCommand): CouponId {
		val entity = CouponEntity.create(command, clock)
		val saved = saveCouponCheckingConstrains(entity)
		log.info("Coupon created: id={} code={} country={} maxUses={}", saved.id, saved.code, saved.country, saved.maxUses)
		return CouponId(saved.id)
	}

	private fun saveCouponCheckingConstrains(entity: CouponEntity): CouponEntity {
		val saved = try {
			couponRepository.saveAndFlush(entity)
		} catch (_: DataIntegrityViolationException) {
			throw CouponCodeAlreadyExistsException(entity.code)
		}
		return saved
	}

	override fun redeem(command: RedeemCouponCommand): RedemptionResult {
		val coupon = couponRepository.findByCode(command.code.value)
		if (coupon == null) {
			log.info(
				"Redemption rejected: outcome=COUPON_NOT_FOUND code={} userId={}",
				command.code.value, command.userId.value,
			)
			return RedemptionResult.CouponNotFound
		}

		// Resolve country before the write transaction so the external call never holds a row lock;
		// fail-closed on error.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		val couponCountry = CountryCode.of(coupon.country)
		if (couponCountry != callerCountry) {
			log.info(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId.value, couponCountry, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(couponCountry.value, callerCountry.value)
		}

		return redemptionExecutor.consume(coupon.id, command.userId.value)
	}
}
