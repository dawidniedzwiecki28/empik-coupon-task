package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the caller's IP for the country check. That check is only as trustworthy as the address it
 * runs on, so by default only the transport remote address is used; the spoofable `X-Forwarded-For`
 * header is honored only when `coupon.rest.trust-client-ip` is set.
 *
 * When trusted, we take the leftmost `X-Forwarded-For` entry as the client. That is correct only behind
 * an ingress that *replaces* the header with the real client address; behind one that *appends* to a
 * client-supplied header the leftmost entry is attacker-controlled, so enabling trust there would let a
 * caller spoof their country. Enable it solely behind a load balancer you know overwrites the header.
 */
@Component
class ClientIpResolver(
	@param:Value("\${coupon.rest.trust-client-ip:false}") private val trustClientIp: Boolean,
) {
	fun resolve(request: HttpServletRequest): IpAddress {
		if (!trustClientIp) return IpAddress.of(request.remoteAddr)
		val forwardedFor = request.getHeader("X-Forwarded-For")
			?.split(',')
			?.map { it.trim() }
			?.firstOrNull { it.isNotEmpty() }
		return IpAddress.of(forwardedFor ?: request.remoteAddr)
	}
}
