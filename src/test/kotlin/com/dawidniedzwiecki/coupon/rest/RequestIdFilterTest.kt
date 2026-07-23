package com.dawidniedzwiecki.coupon.rest

import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestIdFilterTest {

	private val filter = RequestIdFilter()

	@Test
	fun `generates a request id and echoes it on the response when none is supplied`() {
		// given
		val response = MockHttpServletResponse()

		// when
		filter.doFilter(MockHttpServletRequest(), response, MockFilterChain())

		// then
		val id = response.getHeader(RequestIdFilter.HEADER)
		assertNotNull(id)
		assertTrue(id.isNotBlank())
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
}
