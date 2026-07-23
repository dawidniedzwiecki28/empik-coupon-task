package com.dawidniedzwiecki.coupon

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals

/**
 * End-to-end wiring check for the main flows: a real HTTP request travels through the controller,
 * domain logic and PostgreSQL and back. Edge cases are covered at their own layers; this proves the
 * pieces are connected. Geo-IP is faked so the caller's country is deterministic.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class, FakeGeoIpConfig::class)
class CouponE2eTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val coupons: CouponRepository,
	private val redemptions: CouponRedemptionRepository,
	private val geoIp: FakeGeoIpResolver,
) {

	@BeforeEach
	fun reset() {
		redemptions.deleteAll()
		coupons.deleteAll()
		geoIp.reset()
	}

	@Test
	fun `creates a coupon`() {
		createCoupon("WIOSNA").andExpect {
			status { isCreated() }
			jsonPath("$.couponId") { exists() }
		}
	}

	@Test
	fun `redeems a coupon and increments its usage`() {
		// given
		createCoupon("WIOSNA").andExpect { status { isCreated() } }

		// when
		redeem("WIOSNA", UUID.randomUUID()).andExpect { status { isOk() } }

		// then
		assertEquals(1, coupons.findByCode("WIOSNA")!!.currentUses)
	}

	@Test
	fun `rejects a second redemption by the same user`() {
		// given
		createCoupon("WIOSNA").andExpect { status { isCreated() } }
		val user = UUID.randomUUID()

		// expect
		redeem("WIOSNA", user).andExpect { status { isOk() } }
		redeem("WIOSNA", user).andExpect { status { isConflict() } }
	}

	@Test
	fun `rejects a caller from a different country`() {
		// given
		createCoupon("WIOSNA").andExpect { status { isCreated() } }
		geoIp.country = CountryCode.of("DE")

		// expect
		redeem("WIOSNA", UUID.randomUUID()).andExpect { status { isForbidden() } }
	}

	private fun createCoupon(code: String): ResultActionsDsl =
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"$code","maxUses":5,"country":"PL"}"""
		}

	private fun redeem(code: String, user: UUID): ResultActionsDsl =
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"$code","userId":"$user"}"""
		}
}
