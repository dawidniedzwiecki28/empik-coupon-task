package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the caller's IP for the country check. By default only the transport remote address is used;
 * the spoofable `X-Forwarded-For` is honored only when `coupon.rest.trust-client-ip` is set. When trusted
 * we take the leftmost entry, which is safe only behind an ingress that *replaces* the header - behind one
 * that *appends*, that entry is attacker-controlled and a caller could spoof their country.
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
