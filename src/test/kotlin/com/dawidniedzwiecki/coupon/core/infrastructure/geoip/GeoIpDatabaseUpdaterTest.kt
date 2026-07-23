package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GeoIpDatabaseUpdaterTest {

	private val clock = Clock.fixed(Instant.parse("2026-07-15T00:00:00Z"), ZoneOffset.UTC)
	private val requestedPaths = CopyOnWriteArrayList<String>()
	private lateinit var server: HttpServer
	private var responseStatus = 200

	@BeforeEach
	fun startServer() {
		server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		server.createContext("/") { exchange ->
			requestedPaths += exchange.requestURI.path
			val body = if (responseStatus == 200) GeoIpTestFixtures.bundledGzBytes() else ByteArray(0)
			exchange.sendResponseHeaders(responseStatus, if (body.isEmpty()) -1 else body.size.toLong())
			exchange.responseBody.use { it.write(body) }
		}
		server.start()
	}

	@AfterEach
	fun stopServer() = server.stop(0)

	@Test
	fun `fetches and hot-swaps a fresh database, resolving the current year-month in the URL`() {
		// given
		val database = GeoIpDatabase(GeoIpTestFixtures.bundledReader())
		val original = database.reader()
		val updater = updater(database, "$baseUrl/free/dbip-country-lite-{date}.mmdb.gz")

		// when
		updater.refreshOnStartup()

		// then — a new reader was swapped in, and {date} resolved to the fixed clock's month
		assertNotSame(original, database.reader())
		assertTrue(requestedPaths.any { it == "/free/dbip-country-lite-2026-07.mmdb.gz" }, "was: $requestedPaths")
	}

	@Test
	fun `keeps the current database when the download fails`() {
		// given
		responseStatus = 500
		val database = GeoIpDatabase(GeoIpTestFixtures.bundledReader())
		val original = database.reader()
		val updater = updater(database, "$baseUrl/db.mmdb.gz")

		// when
		updater.refreshOnSchedule()

		// then — a failed refresh must not disturb the running database
		assertSame(original, database.reader())
	}

	@Test
	fun `does nothing when no update URL is configured`() {
		// given
		val database = GeoIpDatabase(GeoIpTestFixtures.bundledReader())
		val original = database.reader()

		// when
		updater(database, updateUrl = null).refreshOnStartup()

		// then
		assertSame(original, database.reader())
		assertEquals(0, requestedPaths.size)
	}

	@Test
	fun `does nothing when the update URL is blank`() {
		// given
		val database = GeoIpDatabase(GeoIpTestFixtures.bundledReader())
		val original = database.reader()

		// when
		updater(database, updateUrl = "   ").refreshOnStartup()

		// then
		assertSame(original, database.reader())
		assertEquals(0, requestedPaths.size)
	}

	private val baseUrl get() = "http://127.0.0.1:${server.address.port}"

	private fun updater(database: GeoIpDatabase, updateUrl: String?) =
		GeoIpDatabaseUpdater(database, GeoIpProperties(updateUrl = updateUrl), clock)
}
