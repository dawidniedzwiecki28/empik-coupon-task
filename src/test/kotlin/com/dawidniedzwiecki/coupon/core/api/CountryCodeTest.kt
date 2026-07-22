package com.dawidniedzwiecki.coupon.core.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CountryCodeTest {

	@Test
	fun `normalizes to upper-case`() {
		assertEquals("US", CountryCode.of("us").value)
	}

	@Test
	fun `trims surrounding whitespace`() {
		assertEquals("DE", CountryCode.of("  de  ").value)
	}

	@Test
	fun `equality is by normalized value`() {
		assertEquals(CountryCode.of("pl"), CountryCode.of("PL"))
	}

	@Test
	fun `rejects codes that are not two letters`() {
		assertFailsWith<IllegalArgumentException> { CountryCode.of("USA") }
		assertFailsWith<IllegalArgumentException> { CountryCode.of("P") }
		assertFailsWith<IllegalArgumentException> { CountryCode.of("") }
	}

	@Test
	fun `rejects non-letter codes`() {
		assertFailsWith<IllegalArgumentException> { CountryCode.of("1A") }
		assertFailsWith<IllegalArgumentException> { CountryCode.of("D3") }
	}
}
