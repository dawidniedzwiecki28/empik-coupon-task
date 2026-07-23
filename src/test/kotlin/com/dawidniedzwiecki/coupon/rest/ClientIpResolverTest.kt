package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.InvalidValueException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ClientIpResolverTest {

	@Test
	fun `ignores the forwarded header and uses the remote address when trust is off`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = false)
		val request = mockk<HttpServletRequest> {
			every { remoteAddr } returns "203.0.113.5"
			every { getHeader("X-Forwarded-For") } returns "192.0.2.1"
		}

		// expect — the spoofable header is disregarded
		assertEquals(IpAddress.of("203.0.113.5"), resolver.resolve(request))
	}

	@Test
	fun `uses the first X-Forwarded-For hop when trust is on`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mockk<HttpServletRequest> {
			every { getHeader("X-Forwarded-For") } returns "192.0.2.1, 10.0.0.1"
		}

		// expect
		assertEquals(IpAddress.of("192.0.2.1"), resolver.resolve(request))
	}

	@Test
	fun `skips an empty leading X-Forwarded-For value when trust is on`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mockk<HttpServletRequest> {
			every { getHeader("X-Forwarded-For") } returns ", 203.0.113.5"
		}

		// expect — the first non-empty hop is used
		assertEquals(IpAddress.of("203.0.113.5"), resolver.resolve(request))
	}

	@Test
	fun `rejects a malformed X-Forwarded-For value when trust is on`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mockk<HttpServletRequest> {
			every { getHeader("X-Forwarded-For") } returns "not-an-ip"
		}

		// expect — an unparseable forwarded address is a rejected value (400 at the edge), not a 500
		assertFailsWith<InvalidValueException> { resolver.resolve(request) }
	}

	@Test
	fun `falls back to the remote address when trust is on but no header is present`() {
		// given
		val resolver = ClientIpResolver(trustClientIp = true)
		val request = mockk<HttpServletRequest> {
			every { getHeader("X-Forwarded-For") } returns null
			every { remoteAddr } returns "203.0.113.5"
		}

		// expect
		assertEquals(IpAddress.of("203.0.113.5"), resolver.resolve(request))
	}
}
