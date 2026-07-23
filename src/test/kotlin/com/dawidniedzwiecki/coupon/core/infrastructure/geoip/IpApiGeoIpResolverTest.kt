package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.Fault
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IpApiGeoIpResolverTest {

	private val wireMock = WireMockServer(options().dynamicPort())
	private lateinit var resolver: IpApiGeoIpResolver

	@BeforeEach
	fun setUp() {
		wireMock.start()
		resolver = IpApiGeoIpResolver(
			RestClient.builder().baseUrl(wireMock.baseUrl()).build(),
			Caffeine.newBuilder().build(),
		)
	}

	@AfterEach
	fun tearDown() {
		wireMock.stop()
	}

	@Test
	fun `resolves the country for an IP`() {
		// given
		stubCountry("8.8.8.8", status = 200, body = "US")

		// expect
		assertEquals(CountryCode.of("US"), resolver.resolveCountry(IpAddress.of("8.8.8.8")))
	}

	@Test
	fun `caches the result so the service is called once`() {
		// given
		stubCountry("8.8.8.8", status = 200, body = "US")

		// when
		resolver.resolveCountry(IpAddress.of("8.8.8.8"))
		resolver.resolveCountry(IpAddress.of("8.8.8.8"))

		// then
		wireMock.verify(1, getRequestedFor(urlPathEqualTo("/8.8.8.8/country/")))
	}

	@Test
	fun `fails closed on a non-2xx response`() {
		// given
		stubCountry("8.8.8.8", status = 429, body = "")

		// expect
		assertFailsWith<GeoIpUnavailableException> { resolver.resolveCountry(IpAddress.of("8.8.8.8")) }
	}

	@Test
	fun `fails closed on an unusable body`() {
		// given
		stubCountry("8.8.8.8", status = 200, body = "Undefined")

		// expect
		assertFailsWith<GeoIpUnavailableException> { resolver.resolveCountry(IpAddress.of("8.8.8.8")) }
	}

	@Test
	fun `fails closed on a transport error`() {
		// given
		wireMock.stubFor(
			get(urlPathEqualTo("/8.8.8.8/country/")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)),
		)

		// expect
		assertFailsWith<GeoIpUnavailableException> { resolver.resolveCountry(IpAddress.of("8.8.8.8")) }
	}

	private fun stubCountry(ip: String, status: Int, body: String) {
		wireMock.stubFor(
			get(urlPathEqualTo("/$ip/country/")).willReturn(aResponse().withStatus(status).withBody(body)),
		)
	}
}
