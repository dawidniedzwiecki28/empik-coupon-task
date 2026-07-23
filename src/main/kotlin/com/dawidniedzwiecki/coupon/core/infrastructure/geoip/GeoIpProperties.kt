package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("geoip")
data class GeoIpProperties(
	/** External `.mmdb` used as the startup baseline; blank uses the bundled snapshot. */
	val databasePath: String? = null,
	/** When set, a fresh gzipped `.mmdb` is fetched on startup and on [updateCron]; `{date}` → current `yyyy-MM`. Blank disables auto-update. */
	val updateUrl: String? = null,
	/** Cron for the periodic refresh, in the server time zone. */
	val updateCron: String = "0 0 3 * * *",
)
