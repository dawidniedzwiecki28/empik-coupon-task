package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import java.util.UUID

/**
 * Persistence port for coupon lifecycle (create + lookup). Implemented by infrastructure.
 */
interface CouponCatalog {

	/** @throws com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException on duplicate code. */
	fun save(coupon: Coupon): Coupon

	fun findByCode(normalizedCode: String): Coupon?
}

/**
 * Persistence port for the concurrency-critical redemption step.
 *
 * The implementation must perform the per-user uniqueness check and the usage-limit
 * increment atomically and safely under concurrent access across instances — no data is
 * persisted unless the redemption actually succeeds.
 */
interface CouponRedemptionStore {
	fun consume(couponId: UUID, userId: String): ConsumeOutcome
}

/** Result of a single [CouponRedemptionStore.consume] attempt. */
sealed interface ConsumeOutcome {
	/** Redemption recorded and usage incremented. Carries post-increment counters. */
	data class Redeemed(val currentUses: Int, val maxUses: Int) : ConsumeOutcome

	/** The coupon was already at its maximum uses; nothing was persisted. */
	data object LimitReached : ConsumeOutcome

	/** This user had already redeemed this coupon; nothing was persisted. */
	data object AlreadyRedeemed : ConsumeOutcome
}

/**
 * Port for resolving a caller's country from their IP. Implemented by an infrastructure
 * adapter over an external service; the domain only knows this contract.
 */
interface GeoIpResolver {
	/** @throws com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException when resolution fails. */
	fun resolveCountry(ip: String): CountryCode
}

/** Port for publishing domain events, decoupling the domain from the messaging mechanism. */
interface DomainEventPublisher {
	fun publish(event: Any)
}
