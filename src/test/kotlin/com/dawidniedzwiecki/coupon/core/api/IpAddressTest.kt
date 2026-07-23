package com.dawidniedzwiecki.coupon.core.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// Validation is delegated to Guava, so this covers only our contract (accept + preserve, trim, reject
// as InvalidValueException) - not the IP-parsing matrix, which Guava already tests.
class IpAddressTest {

	@Test
	fun `accepts and preserves a valid IPv4 or IPv6 address`() {
		// expect
		assertEquals("203.0.113.7", IpAddress.of("203.0.113.7").value)
		assertEquals("2001:db8::1", IpAddress.of("2001:db8::1").value)
	}

	@Test
	fun `trims surrounding whitespace`() {
		// expect
		assertEquals("10.0.0.1", IpAddress.of("  10.0.0.1  ").value)
	}

	@Test
	fun `rejects a non-IP value`() {
		// expect
		assertFailsWith<InvalidValueException> { IpAddress.of("not-an-ip") }
	}
}
