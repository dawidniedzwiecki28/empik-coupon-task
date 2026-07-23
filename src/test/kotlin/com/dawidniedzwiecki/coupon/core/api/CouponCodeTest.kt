package com.dawidniedzwiecki.coupon.core.api

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CouponCodeTest {

	@Test
	fun `normalizes to trimmed upper-case`() {
		// expect
		assertEquals("WIOSNA", CouponCode.of("  wiosna  ").value)
	}

	@Test
	fun `is case-insensitively equal`() {
		// expect
		assertEquals(CouponCode.of("wiosna"), CouponCode.of("WIOSNA"))
	}

	@Test
	fun `rejects a blank code`() {
		// expect
		assertFailsWith<IllegalArgumentException> { CouponCode.of("   ") }
	}

	@Test
	fun `rejects a code longer than the max length`() {
		// expect
		assertFailsWith<IllegalArgumentException> { CouponCode.of("A".repeat(CouponCode.MAX_LENGTH + 1)) }
	}
}
