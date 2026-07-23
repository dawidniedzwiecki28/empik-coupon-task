package com.dawidniedzwiecki.coupon.config

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpProperties
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.net.URI

@Configuration
@EnableConfigurationProperties(GeoIpProperties::class)
class GeoIpConfiguration {

	@Bean
	fun geoIpRestClient(properties: GeoIpProperties): RestClient {
		requireSecureBaseUrl(properties.baseUrl)
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(properties.timeout)
			setReadTimeout(properties.timeout)
		}
		return RestClient.builder().baseUrl(properties.baseUrl).requestFactory(requestFactory).build()
	}

	@Bean
	fun geoIpCache(properties: GeoIpProperties): Cache<String, CountryCode> =
		Caffeine.newBuilder()
			.maximumSize(properties.cache.maximumSize)
			.expireAfterWrite(properties.cache.ttl)
			.build()

	/** Client IPs and country lookups must not travel in cleartext; only allow http for local/test hosts. */
	private fun requireSecureBaseUrl(baseUrl: String) {
		val uri = URI.create(baseUrl)
		val localHost = uri.host in setOf("localhost", "127.0.0.1", "[::1]")
		require(uri.scheme == "https" || (uri.scheme == "http" && localHost)) {
			"geoip.base-url must use HTTPS (was: $baseUrl)"
		}
	}
}
