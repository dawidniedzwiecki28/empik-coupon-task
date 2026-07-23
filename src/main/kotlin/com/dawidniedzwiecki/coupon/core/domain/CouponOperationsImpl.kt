package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponEntity
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Stateless; the redemption invariants are enforced by atomic SQL inside a short transaction, so it scales horizontally. */
@Service
class CouponOperationsImpl(
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
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

	/**
	 * @Transactional groups the insert, the conditional increment, and the compensating delete into one
	 * atomic unit — without it each repository write would commit in its own transaction and a crash
	 * mid-way could orphan a redemption. Geo-IP resolution runs inside this transaction, so it must stay
	 * a local, fast lookup; a remote call here would hold a DB connection across the network.
	 */
	@Transactional
	override fun redeem(command: RedeemCouponCommand): RedemptionResult {
		val coupon = couponRepository.findByCode(command.code.value)
		if (coupon == null) {
			log.debug(
				"Redemption rejected: outcome=COUPON_NOT_FOUND code={} userId={}",
				command.code.value, command.userId.value,
			)
			return RedemptionResult.CouponNotFound
		}

		// Fail-closed on error: an unverified country never redeems.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		if (coupon.country != callerCountry.value) {
			log.debug(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId.value, coupon.country, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(coupon.country, callerCountry.value)
		}

		return consume(coupon.id, command.userId.value)
	}

	/**
	 * The redemption write. Insert-first (ON CONFLICT DO NOTHING) rejects a repeat user before the counter
	 * moves; the atomic conditional increment caps usage; on a full coupon the tentative redemption is
	 * undone within the caller's transaction.
	 */
	private fun consume(couponId: UUID, userId: UUID): RedemptionResult {
		if (redemptionRepository.insertIfAbsent(couponId, userId, Instant.now(clock)) == 0) {
			log.debug("Redemption rejected: outcome=ALREADY_REDEEMED couponId={} userId={}", couponId, userId)
			return RedemptionResult.AlreadyRedeemedByUser
		}
		if (couponRepository.incrementUsesIfBelowMax(couponId) == 0) {
			redemptionRepository.deleteRedemption(couponId, userId)
			log.debug("Redemption rejected: outcome=LIMIT_REACHED couponId={} userId={}", couponId, userId)
			return RedemptionResult.LimitReached
		}
		log.debug("Redemption succeeded: couponId={} userId={}", couponId, userId)
		return RedemptionResult.Success
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
