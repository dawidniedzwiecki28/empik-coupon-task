package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the caller's IP for country authorization.
 *
 * The country check is only as trustworthy as the address it runs on, so by default only the
 * transport remote address is used. Client-supplied hints — the `ipOverride` field and the
 * `X-Forwarded-For` header — are honored only when `coupon.rest.trust-client-ip` is enabled, which
 * is safe solely behind an ingress that overwrites `X-Forwarded-For`. Enabling it on a directly
 * exposed service would let a caller spoof its country and bypass the restriction.
 */
@Component
class ClientIpResolver(
	@param:Value("\${coupon.rest.trust-client-ip:false}") private val trustClientIp: Boolean,
) {
	fun resolve(override: String?, request: HttpServletRequest): IpAddress {
		if (!trustClientIp) return IpAddress.of(request.remoteAddr)
		val raw = override
			?: request.getHeader("X-Forwarded-For")?.substringBefore(",")?.trim()?.takeIf { it.isNotEmpty() }
			?: request.remoteAddr
		return IpAddress.of(raw)
	}
}
