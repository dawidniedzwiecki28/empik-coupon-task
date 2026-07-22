package com.dawidniedzwiecki.coupon.core.api

/** Thrown when creating a coupon whose normalized code already exists. */
class CouponCodeAlreadyExistsException(val code: String) :
	RuntimeException("A coupon with code '$code' already exists")

/** Caller's country could not be resolved from their IP — distinct from the CountryNotAllowed outcome. */
class GeoIpUnavailableException(val ip: String, cause: Throwable? = null) :
	RuntimeException("Unable to resolve country for IP '$ip'", cause)
