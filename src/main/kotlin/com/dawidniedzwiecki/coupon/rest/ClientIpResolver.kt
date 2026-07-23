package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the caller's IP for the country check. That check is only as trustworthy as the address it
 * runs on, so by default only the transport remote address is used; the spoofable `X-Forwarded-For`
 * header is honored only when `coupon.rest.trust-client-ip` is set — safe solely behind an ingress
 * that overwrites it.
 */
@Component
class ClientIpResolver(
	@param:Value("\${coupon.rest.trust-client-ip:false}") private val trustClientIp: Boolean,
) {
	fun resolve(request: HttpServletRequest): IpAddress {
		if (!trustClientIp) return IpAddress.of(request.remoteAddr)
		val forwardedFor = request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
		return IpAddress.of(forwardedFor ?: request.remoteAddr)
	}
}
