package com.dawidniedzwiecki.coupon.core.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IpAddressTest {

	@Test
	fun `accepts a valid IPv4 address`() {
		// expect
		assertEquals("203.0.113.7", IpAddress.of("203.0.113.7").value)
	}

	@Test
	fun `accepts a valid IPv6 address`() {
		// expect
		assertEquals("2001:db8::1", IpAddress.of("2001:db8::1").value)
	}

	@Test
	fun `accepts IPv6 shorthands and embedded IPv4`() {
		// expect
		assertEquals("::1", IpAddress.of("::1").value)
		assertEquals("::", IpAddress.of("::").value)
		assertEquals("::ffff:192.168.1.1", IpAddress.of("::ffff:192.168.1.1").value)
	}

	@Test
	fun `rejects malformed IPv6 addresses`() {
		// expect
		assertFailsWith<IllegalArgumentException> { IpAddress.of(":") }
		assertFailsWith<IllegalArgumentException> { IpAddress.of(":::") }
		assertFailsWith<IllegalArgumentException> { IpAddress.of("2001:db8:::1") }
		assertFailsWith<IllegalArgumentException> { IpAddress.of("12345::") }
		assertFailsWith<IllegalArgumentException> { IpAddress.of("gggg::1") }
	}

	@Test
	fun `trims surrounding whitespace`() {
		// expect
		assertEquals("10.0.0.1", IpAddress.of("  10.0.0.1  ").value)
	}

	@Test
	fun `rejects an out-of-range IPv4 octet`() {
		// expect
		assertFailsWith<IllegalArgumentException> { IpAddress.of("203.0.113.999") }
	}

	@Test
	fun `rejects an IPv4 octet with a leading zero`() {
		// expect
		assertFailsWith<IllegalArgumentException> { IpAddress.of("203.0.113.01") }
	}

	@Test
	fun `rejects a non-IP string`() {
		// expect
		assertFailsWith<IllegalArgumentException> { IpAddress.of("not-an-ip") }
	}

	@Test
	fun `rejects a blank value`() {
		// expect
		assertFailsWith<IllegalArgumentException> { IpAddress.of("   ") }
	}
}
