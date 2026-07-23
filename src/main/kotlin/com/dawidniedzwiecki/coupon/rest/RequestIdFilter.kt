package com.dawidniedzwiecki.coupon.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Assigns every request a correlation id: the caller's `X-Request-Id` when it is present and well-formed,
 * otherwise a fresh UUID. The id goes into the SLF4J MDC (so each log line carries it — see the
 * `logging.pattern.level` in application.yml) and is echoed on the response so a client can correlate.
 *
 * A caller-supplied value is trusted for tracing only, never for authorization, and is validated to a
 * short id charset so it can't forge log lines (CRLF injection) or bloat the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {

	override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
		val requestId = sanitize(request.getHeader(HEADER)) ?: UUID.randomUUID().toString()
		MDC.put(MDC_KEY, requestId)
		response.setHeader(HEADER, requestId)
		try {
			filterChain.doFilter(request, response)
		} finally {
			MDC.remove(MDC_KEY)
		}
	}

	private fun sanitize(raw: String?): String? =
		raw?.trim()?.takeIf { it.length in 1..MAX_LENGTH && it.all(::isIdChar) }

	private fun isIdChar(c: Char): Boolean = c.isLetterOrDigit() || c == '-'

	companion object {
		const val HEADER = "X-Request-Id"
		const val MDC_KEY = "requestId"
		private const val MAX_LENGTH = 64
	}
}
