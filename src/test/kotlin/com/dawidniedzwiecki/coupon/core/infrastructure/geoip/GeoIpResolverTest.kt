package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CountryResponse
import com.maxmind.geoip2.record.Country
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.io.IOException
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

	@Test
	fun `fails closed when the lookup raises an IO error`() {
		// given
		val reader = mockk<DatabaseReader> { every { country(any()) } throws IOException("read failed") }

		// expect
		assertFailsWith<GeoIpUnavailableException> { resolverBackedBy(reader).resolveCountry(IpAddress.of("8.8.8.8")) }
	}

	@Test
	fun `fails closed when the address maps to no country code`() {
		// given
		val country = mockk<Country> { every { isoCode() } returns null }
		val response = mockk<CountryResponse> { every { country() } returns country }
		val reader = mockk<DatabaseReader> { every { country(any()) } returns response }

		// expect
		assertFailsWith<GeoIpUnavailableException> { resolverBackedBy(reader).resolveCountry(IpAddress.of("8.8.8.8")) }
	}

	private fun resolverBackedBy(reader: DatabaseReader) = GeoIpResolver(GeoIpDatabase(reader))
}
