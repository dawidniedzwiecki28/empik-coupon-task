package com.dawidniedzwiecki.coupon.rest

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequestIdFilterTest {

	private val filter = RequestIdFilter()

	@Test
	fun `generates a request id and echoes it on the response when none is supplied`() {
		// given
		val response = MockHttpServletResponse()

		// when
		filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())

		// then — a canonical UUID, not just any non-blank string
		val id = response.getHeader(RequestIdFilter.HEADER)
		assertNotNull(id)
		assertEquals(id, UUID.fromString(id).toString())
	}

	@Test
	fun `reuses a well-formed caller-supplied request id`() {
		// given
		val request = MockHttpServletRequest().apply { addHeader(RequestIdFilter.HEADER, "trace-abc123") }
		val response = MockHttpServletResponse()

		// when
		filter.doFilter(request, response, MockFilterChain())

		// then
		assertEquals("trace-abc123", response.getHeader(RequestIdFilter.HEADER))
	}

	@Test
	fun `replaces an unsafe caller-supplied request id to prevent log forging`() {
		// given — newline + spaces would let a caller inject fake log lines
		val request = MockHttpServletRequest().apply { addHeader(RequestIdFilter.HEADER, "bad\nid with spaces") }
		val response = MockHttpServletResponse()

		// when
		filter.doFilter(request, response, MockFilterChain())

		// then
		val id = response.getHeader(RequestIdFilter.HEADER)
		assertNotNull(id)
		assertTrue(id.all { it.isLetterOrDigit() || it == '-' })
	}

	@Test
	fun `exposes the id in the MDC during the chain and clears it afterwards`() {
		// given — capture what downstream code would see in the MDC
		val request = MockHttpServletRequest().apply { addHeader(RequestIdFilter.HEADER, "trace-xyz") }
		var idDuringChain: String? = null
		val chain = FilterChain { _, _ -> idDuringChain = MDC.get(RequestIdFilter.MDC_KEY) }

		// when
		filter.doFilter(request, MockHttpServletResponse(), chain)

		// then — present for the duration of the request, then removed so the (pooled) thread doesn't leak it
		assertEquals("trace-xyz", idDuringChain)
		assertNull(MDC.get(RequestIdFilter.MDC_KEY))
	}
}
