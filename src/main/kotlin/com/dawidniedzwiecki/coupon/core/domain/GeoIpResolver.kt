package com.dawidniedzwiecki.coupon.core.domain

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.IpAddress

/** Resolves a caller's country from their IP. */
interface GeoIpResolver {
	/** @throws com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException when resolution fails. */
	fun resolveCountry(ip: IpAddress): CountryCode
}
