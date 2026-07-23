package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.maxmind.geoip2.exception.GeoIp2Exception
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress

/** [GeoIpResolver] backed by a local, in-memory IP→country database - a sub-microsecond lookup, no network call. */
@Component
class DatabaseGeoIpResolver(private val database: GeoIpDatabase) : GeoIpResolver {

	override fun resolveCountry(ip: IpAddress): CountryCode =
		try {
			// IpAddress is already syntactically valid, so this parses a literal - no DNS lookup.
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
