package com.dawidniedzwiecki.coupon.core.api

/** Thrown when creating a coupon whose normalized code already exists. */
class CouponCodeAlreadyExistsException(val code: String) :
	RuntimeException("A coupon with code '$code' already exists")

/**
 * Thrown when the caller's country cannot be resolved from their IP.
 *
 * This is an exceptional condition (external dependency failure), distinct from the
 * expected [RedemptionResult.CountryNotAllowed] business outcome.
 */
class GeoIpUnavailableException(val ip: String, cause: Throwable? = null) :
	RuntimeException("Unable to resolve country for IP '$ip'", cause)
