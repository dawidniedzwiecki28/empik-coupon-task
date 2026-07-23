package com.dawidniedzwiecki.coupon.config

import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpDatabase
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpDatabaseUpdater
import com.dawidniedzwiecki.coupon.core.infrastructure.geoip.GeoIpProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Clock

/** Enables periodic geo-IP database refresh only when a source URL is configured (`geoip.update-url`). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "geoip", name = ["update-url"])
class GeoIpUpdateConfiguration {

	@Bean
	fun geoIpDatabaseUpdater(database: GeoIpDatabase, properties: GeoIpProperties, clock: Clock) =
		GeoIpDatabaseUpdater(database, properties, clock)
}
