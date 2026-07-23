package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.maxmind.geoip2.exception.GeoIp2Exception
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress

/**
 * Resolves a caller's country from a local IP→country database (MaxMind-format, in-memory, no network
 * call or quota). Fail-closed: an unmapped address (e.g. a private/reserved range) or any lookup error
 * raises GeoIpUnavailableException so a country-restricted coupon is never redeemed on an unverified
 * country. Used directly by the domain — like the Spring Data repositories, no extra port abstraction.
 */
@Component
open class GeoIpResolver(private val database: GeoIpDatabase) {

	open fun resolveCountry(ip: IpAddress): CountryCode =
		try {
			// IpAddress is already syntactically valid, so this parses a literal — no DNS lookup.
			// country()/isoCode() are the record accessors; the .country/.isoCode getters are deprecated.
			val isoCode = database.reader().country(InetAddress.getByName(ip.value)).country().isoCode()
			// A null/blank code (address present but no country) fails closed via CountryCode.of.
			CountryCode.of(isoCode.orEmpty())
		} catch (e: IOException) {
			throw GeoIpUnavailableException(ip.value, e)
		} catch (e: GeoIp2Exception) {
			throw GeoIpUnavailableException(ip.value, e)
		} catch (e: IllegalArgumentException) {
			throw GeoIpUnavailableException(ip.value, e)
		}
}
