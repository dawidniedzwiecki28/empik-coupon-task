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
		assertSame(initial, database.reader()) // baseline before the swap

		// when
		database.swap(replacement)

		// then
		assertSame(replacement, database.reader())
	}
}
