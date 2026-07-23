package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

@Service
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
		val saved = couponRepository.saveEnforcingUniqueCode(entity)
		log.info("Coupon created: id={} code={} country={} maxUses={}", saved.id, saved.code, saved.country, saved.maxUses)
		return CouponId(saved.id)
	}

	override fun redeem(command: RedeemCouponCommand): RedemptionResult {
		val coupon = couponRepository.findByCode(command.code.value)
		if (coupon == null) {
			log.debug(
				"Redemption rejected: outcome=COUPON_NOT_FOUND code={} userId={}",
				command.code.value, command.userId.value,
			)
			return RedemptionResult.CouponNotFound
		}

		// Resolve country before the write transaction so the external call never holds a row lock;
		// fail-closed on error.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		if (coupon.country != callerCountry.value) {
			log.debug(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId.value, coupon.country, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(coupon.country, callerCountry.value)
		}

		return redemptionExecutor.consume(coupon.id, command.userId.value)
	}

	private fun CouponRepository.saveEnforcingUniqueCode(entity: CouponEntity): CouponEntity =
		try {
			saveAndFlush(entity)
		} catch (ex: DataIntegrityViolationException) {
			// Only the unique-code constraint means "already exists"; any other integrity
			// violation (check constraints, PK) is unexpected and must surface as-is.
			val constraint = (ex.cause as? ConstraintViolationException)?.constraintName
			if (CouponEntity.UNIQUE_CODE_CONSTRAINT.equals(constraint, ignoreCase = true)) {
				throw CouponCodeAlreadyExistsException(entity.code)
			}
			throw ex
		}
}
