package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * Resolves the caller's IP for country authorization.
 *
 * The country check is only as trustworthy as the address it runs on, so by default only the
 * transport remote address is used. The `X-Forwarded-For` header is honored only when
 * `coupon.rest.trust-client-ip` is enabled, which is safe solely behind an ingress that overwrites
 * that header. Enabling it on a directly exposed service would let a caller spoof its country and
 * bypass the restriction.
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
