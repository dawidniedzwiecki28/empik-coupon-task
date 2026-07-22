package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import java.util.UUID

/** Persistence port for coupon create + lookup. */
interface CouponCatalog {
	/** @throws com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException on duplicate code. */
	fun save(coupon: Coupon): Coupon

	fun findByCode(normalizedCode: String): Coupon?
}

/**
 * Concurrency-critical redemption step: the per-user uniqueness check and the usage-limit
 * increment must be atomic and safe across instances, persisting nothing unless it succeeds.
 */
interface CouponRedemptionStore {
	fun consume(couponId: UUID, userId: String): ConsumeOutcome
}

sealed interface ConsumeOutcome {
	/** Redemption recorded; carries post-increment counters. */
	data class Redeemed(val currentUses: Int, val maxUses: Int) : ConsumeOutcome

	/** Coupon already at max uses; nothing persisted. */
	data object LimitReached : ConsumeOutcome

	/** User had already redeemed this coupon; nothing persisted. */
	data object AlreadyRedeemed : ConsumeOutcome
}

/** Resolves a caller's country from their IP. */
interface GeoIpResolver {
	/** @throws com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException when resolution fails. */
	fun resolveCountry(ip: String): CountryCode
}

/** Publishes domain events, decoupling the domain from the messaging mechanism. */
interface DomainEventPublisher {
	fun publish(event: Any)
}
