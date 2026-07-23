package com.dawidniedzwiecki.coupon.config

import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpDatabase
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpProperties
import com.maxmind.geoip2.DatabaseReader
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.File
import java.util.zip.GZIPInputStream

@Configuration
@EnableConfigurationProperties(GeoIpProperties::class)
class GeoIpConfiguration {

	private val log = LoggerFactory.getLogger(javaClass)

	/**
	 * The startup baseline database, always available even offline: an external file if configured,
	 * otherwise the bundled classpath snapshot. [GeoIpDatabase] allows it to be hot-swapped later.
	 */
	@Bean
	fun geoIpDatabase(properties: GeoIpProperties): GeoIpDatabase = GeoIpDatabase(loadBaseline(properties))

	private fun loadBaseline(properties: GeoIpProperties): DatabaseReader {
		val externalPath = properties.databasePath
		if (!externalPath.isNullOrBlank()) {
			log.info("Loading geo-IP database from external file: {}", externalPath)
			return DatabaseReader.Builder(File(externalPath)).build()
		}
		log.info("Loading bundled geo-IP database: {}", BUNDLED_DATABASE)
		val resource = javaClass.getResourceAsStream(BUNDLED_DATABASE)
			?: error("Bundled geo-IP database not found on classpath: $BUNDLED_DATABASE")
		return resource.use { gz -> DatabaseReader.Builder(GZIPInputStream(gz)).build() }
	}

	private companion object {
		/** DB-IP IP-to-Country Lite (CC-BY-4.0), committed gzipped; see NOTICE. */
		const val BUNDLED_DATABASE = "/geoip/dbip-country-lite.mmdb.gz"
	}
}
