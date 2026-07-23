package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.github.benmanes.caffeine.cache.Cache
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

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

	override fun resolveCountry(ip: IpAddress): CountryCode =
		cache.getIfPresent(ip.value) ?: fetch(ip).also { cache.put(ip.value, it) }

	private fun fetch(ip: IpAddress): CountryCode {
		val body = try {
			restClient.get().uri("/{ip}/country/", ip.value).retrieve().body(String::class.java)
		} catch (e: Exception) {
			throw GeoIpUnavailableException(ip.value, e)
		}
		return runCatching { CountryCode.of(body.orEmpty()) }
			.getOrElse { throw GeoIpUnavailableException(ip.value, it) }
	}
}
