package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("geoip")
data class GeoIpProperties(
	val baseUrl: String = "https://ipapi.co",
	val timeout: Duration = Duration.ofSeconds(2),
	val cache: Cache = Cache(),
) {
	data class Cache(
		val maximumSize: Long = 50_000,
		val ttl: Duration = Duration.ofHours(24),
	)
}
