package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.github.benmanes.caffeine.cache.Cache
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Resolves country via ipapi.co over HTTPS, cached per instance. Fail-closed: any transport error,
 * non-2xx, or unusable body raises GeoIpUnavailableException so a country-restricted coupon is never
 * redeemed on an unverified country.
 */
@Component
class IpApiGeoIpResolver(
	private val restClient: RestClient,
	private val cache: Cache<String, CountryCode>,
) : GeoIpResolver {

	// Atomic load: concurrent misses for the same IP share one upstream call; failures are not cached.
	override fun resolveCountry(ip: IpAddress): CountryCode = cache.get(ip.value) { fetch(ip) }

	private fun fetch(ip: IpAddress): CountryCode {
		val body = try {
			restClient.get().uri("/{ip}/country/", ip.value).retrieve().body(String::class.java)
		} catch (e: RestClientException) {
			throw GeoIpUnavailableException(ip.value, e)
		}
		return try {
			CountryCode.of(body.orEmpty())
		} catch (e: IllegalArgumentException) {
			throw GeoIpUnavailableException(ip.value, e)
		}
	}
}
