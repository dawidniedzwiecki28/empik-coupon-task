package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.IpAddress
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.test.assertEquals

class ClientIpResolverTest {

	@Test
	fun `ignores client-supplied hints and uses the remote address when trust is off`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = false)
		val request = mock<HttpServletRequest> {
			on { remoteAddr } doReturn "203.0.113.5"
			on { getHeader("X-Forwarded-For") } doReturn "192.0.2.1"
		}

		// expect — override and header are both disregarded
		assertEquals(IpAddress.of("203.0.113.5"), resolver.resolve(override = "198.51.100.9", request = request))
	}

	@Test
	fun `prefers the override when trust is on`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mock<HttpServletRequest>()

		// expect
		assertEquals(IpAddress.of("198.51.100.9"), resolver.resolve(override = "198.51.100.9", request = request))
	}

	@Test
	fun `uses the first X-Forwarded-For hop when trust is on and no override is given`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mock<HttpServletRequest> {
			on { getHeader("X-Forwarded-For") } doReturn "192.0.2.1, 10.0.0.1"
		}

		// expect
		assertEquals(IpAddress.of("192.0.2.1"), resolver.resolve(override = null, request = request))
	}

	@Test
	fun `falls back to the remote address when trust is on but no hints are present`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mock<HttpServletRequest> {
			on { remoteAddr } doReturn "203.0.113.5"
		}

		// expect
		assertEquals(IpAddress.of("203.0.113.5"), resolver.resolve(override = null, request = request))
	}
}
