package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GeoIpResolverTest {

	private val resolver = GeoIpResolver(GeoIpDatabase(GeoIpTestFixtures.bundledReader()))

	@Test
	fun `resolves the country of a public IP from the bundled database`() {
		// expect — 8.8.8.8 (Google public DNS) is a stable, well-known US allocation
		assertEquals(CountryCode.of("US"), resolver.resolveCountry(IpAddress.of("8.8.8.8")))
	}

	@Test
	fun `fails closed for a private address not present in the database`() {
		// expect — reserved ranges have no country, so we reject rather than guess
		assertFailsWith<GeoIpUnavailableException> { resolver.resolveCountry(IpAddress.of("10.0.0.1")) }
	}
}
