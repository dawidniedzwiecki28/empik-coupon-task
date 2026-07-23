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

@Configuration
@EnableConfigurationProperties(GeoIpProperties::class)
class GeoIpConfiguration {

	@Bean
	fun geoIpRestClient(properties: GeoIpProperties): RestClient {
		val timeoutMillis = properties.timeout.toMillis().toInt()
		val requestFactory = SimpleClientHttpRequestFactory().apply {
			setConnectTimeout(timeoutMillis)
			setReadTimeout(timeoutMillis)
		}
		return RestClient.builder().baseUrl(properties.baseUrl).requestFactory(requestFactory).build()
	}

	@Bean
	fun geoIpCache(properties: GeoIpProperties): Cache<String, CountryCode> =
		Caffeine.newBuilder()
			.maximumSize(properties.cache.maximumSize)
			.expireAfterWrite(properties.cache.ttl)
			.build()
}
