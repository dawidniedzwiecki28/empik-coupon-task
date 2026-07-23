package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import org.junit.jupiter.api.Test
import kotlin.test.assertSame

class GeoIpDatabaseTest {

	@Test
	fun `swap atomically replaces the active reader`() {
		// given
		val initial = GeoIpTestFixtures.bundledReader()
		val replacement = GeoIpTestFixtures.bundledReader()
		val database = GeoIpDatabase(initial)

		// when
		assertSame(initial, database.reader())
		database.swap(replacement)

		// then
		assertSame(replacement, database.reader())
	}
}
