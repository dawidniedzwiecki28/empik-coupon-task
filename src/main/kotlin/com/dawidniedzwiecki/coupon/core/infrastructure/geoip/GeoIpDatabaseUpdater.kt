package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.maxmind.geoip2.DatabaseReader
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Clock
import java.time.Duration
import java.time.YearMonth
import java.util.zip.GZIPInputStream

/**
 * Fetches a fresh IP→country database and hot-swaps it into [GeoIpDatabase] on startup and on the
 * configured cron. Best-effort: any failure is logged and the current database is kept. Created only
 * when `geoip.update-url` is set; otherwise the app runs on the bundled snapshot.
 */
@Component
@ConditionalOnProperty(prefix = "geoip", name = ["update-url"])
class GeoIpDatabaseUpdater(
	private val database: GeoIpDatabase,
	private val properties: GeoIpProperties,
	private val clock: Clock,
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

	@EventListener(ApplicationReadyEvent::class)
	fun refreshOnStartup() = refresh("startup")

	@Scheduled(cron = "\${geoip.update-cron}")
	fun refreshOnSchedule() = refresh("schedule")

	private fun refresh(trigger: String) {
		val url = resolveUrl() ?: return
		try {
			val reader = download(url)
			database.swap(reader)
			log.info("Geo-IP database refreshed ({}) from {}", trigger, url)
		} catch (e: InterruptedException) {
			Thread.currentThread().interrupt()
			log.warn("Geo-IP database refresh ({}) interrupted; keeping current database", trigger)
		} catch (e: Exception) {
			// Broad on purpose: a background refresh must never escape; ERROR so a stalled refresh is visible.
			log.error("Geo-IP database refresh ({}) failed; keeping current database", trigger, e)
		}
	}

	/** Substitutes `{date}` with the current year-month so a monthly-published URL stays current. */
	private fun resolveUrl(): String? {
		val template = properties.updateUrl?.takeIf { it.isNotBlank() } ?: return null
		return template.replace("{date}", YearMonth.now(clock).toString())
	}

	private fun download(url: String): DatabaseReader {
		val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build()
		val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
		require(response.statusCode() == 200) { "unexpected HTTP status ${response.statusCode()} from $url" }
		return DatabaseReader.Builder(GZIPInputStream(ByteArrayInputStream(response.body()))).build()
	}
}
