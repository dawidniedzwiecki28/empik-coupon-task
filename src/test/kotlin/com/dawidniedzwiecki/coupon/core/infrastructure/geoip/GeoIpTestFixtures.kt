package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.maxmind.geoip2.DatabaseReader
import java.io.ByteArrayInputStream
import java.util.zip.GZIPInputStream

/** Shared access to the committed DB-IP snapshot used across geo-IP tests. */
object GeoIpTestFixtures {

	private const val BUNDLED_DATABASE = "/geoip/dbip-country-lite.mmdb.gz"

	fun bundledGzBytes(): ByteArray =
		javaClass.getResourceAsStream(BUNDLED_DATABASE)?.use { it.readBytes() }
			?: error("bundled geo-IP database missing from test classpath")

	fun bundledReader(): DatabaseReader =
		DatabaseReader.Builder(GZIPInputStream(ByteArrayInputStream(bundledGzBytes()))).build()

	/** A GeoIpDatabase for fakes that fully override resolveCountry, where the wrapped reader is never consulted. */
	fun dummyDatabase(): GeoIpDatabase = GeoIpDatabase(bundledReader())
}
