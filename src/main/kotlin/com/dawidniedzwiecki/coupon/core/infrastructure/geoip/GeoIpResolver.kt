package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress

/**
 * Outbound port: resolves a caller's country from their IP. Fail-closed by contract — an address that
 * can't be mapped raises [GeoIpUnavailableException] rather than returning a guess, so a restricted
 * coupon is never redeemed on an unverified country. The port lets the domain (and tests) depend on the
 * capability, not the [DatabaseGeoIpResolver] adapter.
 */
interface GeoIpResolver {

	/** @throws GeoIpUnavailableException if the country can't be determined. */
	fun resolveCountry(ip: IpAddress): CountryCode
}
