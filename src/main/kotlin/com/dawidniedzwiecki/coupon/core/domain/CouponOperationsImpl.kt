package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CouponView
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

/** Stateless; redemption invariants are enforced by atomic SQL inside a short transaction. */
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
		return CouponId(saved.persistedId)
	}

	@Transactional(readOnly = true)
	override fun findCoupon(id: CouponId): CouponView? =
		couponRepository.findById(id.value).map { it.toView() }.orElse(null)

	// One transaction spans the insert, conditional increment, and compensating delete so the tentative
	// redemption's undo is atomic. The country lookup runs inside it too, but that's safe by design: it is
	// a local, in-memory database read (no network call), so the row lock is held for microseconds, not an
	// external round trip.
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

		// Fail-closed: an unverified country never redeems.
		val callerCountry = geoIpResolver.resolveCountry(command.clientIp)
		if (coupon.country != callerCountry.value) {
			log.debug(
				"Redemption rejected: outcome=COUNTRY_NOT_ALLOWED code={} userId={} required={} caller={}",
				coupon.code, command.userId.value, coupon.country, callerCountry,
			)
			return RedemptionResult.CountryNotAllowed(coupon.country, callerCountry.value)
		}

		// Fast-path a sold-out coupon: current_uses only ever grows, so a full snapshot is final. This sheds
		// the insert + compensating-delete churn from doomed requests; the atomic UPDATE in consume() stays
		// the real guard against the fill-up race.
		if (coupon.currentUses >= coupon.maxUses) {
			log.debug("Redemption rejected: outcome=LIMIT_REACHED couponId={} userId={}", coupon.persistedId, command.userId.value)
			return RedemptionResult.LimitReached
		}

		return consume(coupon.persistedId, command.userId.value)
	}

	// Insert-first rejects a repeat user before the counter moves; if the coupon is already full the
	// conditional increment changes nothing and the tentative redemption is undone in the same transaction.
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

	private fun CouponEntity.toView(): CouponView =
		CouponView(
			id = persistedId,
			code = code,
			country = country,
			maxUses = maxUses,
			currentUses = currentUses,
			createdAt = createdAt,
		)

	private fun CouponRepository.saveEnforcingUniqueCode(entity: CouponEntity): CouponEntity =
		try {
			saveAndFlush(entity)
		} catch (ex: DataIntegrityViolationException) {
			// Only the unique-code constraint means "already exists"; any other violation must surface as-is.
			val constraint = (ex.cause as? ConstraintViolationException)?.constraintName
			if (CouponEntity.UNIQUE_CODE_CONSTRAINT.equals(constraint, ignoreCase = true)) {
				throw CouponCodeAlreadyExistsException(entity.code)
			}
			throw ex
		}
}
