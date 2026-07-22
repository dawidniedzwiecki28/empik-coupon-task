package com.dawidniedzwiecki.coupon.core.domain

/** Publishes domain events, decoupling the domain from the messaging mechanism. */
interface DomainEventPublisher {
	fun publish(event: Any)
}
