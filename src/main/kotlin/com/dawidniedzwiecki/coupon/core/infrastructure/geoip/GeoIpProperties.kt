package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("geoip")
data class GeoIpProperties(
	/**
	 * Optional path to an external IP→country `.mmdb` file used as the startup baseline. When blank,
	 * the bundled classpath snapshot is used.
	 */
	val databasePath: String? = null,
	/**
	 * When set, a fresh gzipped `.mmdb` is fetched from here on startup and on [updateCron], replacing
	 * the in-memory database. Blank disables auto-update (the bundled/external baseline is used as-is).
	 * A `{date}` placeholder is replaced with the current year-month (`yyyy-MM`), e.g.
	 * `https://download.db-ip.com/free/dbip-country-lite-{date}.mmdb.gz`.
	 */
	val updateUrl: String? = null,
	/** Cron expression for the periodic refresh (server time zone); default 03:00 daily. */
	val updateCron: String = "0 0 3 * * *",
)
