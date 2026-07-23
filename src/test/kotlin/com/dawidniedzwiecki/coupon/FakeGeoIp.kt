package com.dawidniedzwiecki.coupon

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpResolver
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

/** Deterministic geo-IP: every caller resolves to [country], or fails when [available] is false. */
class FakeGeoIpResolver : GeoIpResolver {
	@Volatile
	var country: CountryCode = CountryCode.of("PL")

	@Volatile
	var available: Boolean = true

	override fun resolveCountry(ip: IpAddress): CountryCode {
		if (!available) throw GeoIpUnavailableException(ip.value)
		return country
	}

	fun reset() {
		country = CountryCode.of("PL")
		available = true
	}
}

@TestConfiguration
class FakeGeoIpConfig {
	@Bean
	@Primary
	fun fakeGeoIpResolver(): FakeGeoIpResolver = FakeGeoIpResolver()
}
