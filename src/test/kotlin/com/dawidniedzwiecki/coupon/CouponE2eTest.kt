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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.assertEquals

/** End-to-end wiring for the main flows: HTTP → controller → domain → PostgreSQL → back; geo-IP faked. */
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
		// expect
		createCoupon("WIOSNA").andExpect {
			status { isCreated() }
			jsonPath("$.couponId") { exists() }
		}
	}

	@Test
	fun `creates then reads a coupon back via the Location header`() {
		// given
		val location = createCoupon("READBACK")
			.andExpect { status { isCreated() } }
			.andReturn().response.getHeader("Location")!!

		// expect — GET on the advertised location returns the coupon's current state
		mockMvc.get(location).andExpect {
			status { isOk() }
			jsonPath("$.code") { value("READBACK") }
			jsonPath("$.country") { value("PL") }
			jsonPath("$.currentUses") { value(0) }
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

		// when
		redeem("WIOSNA", user).andExpect { status { isOk() } }

		// then — the second attempt is rejected
		redeem("WIOSNA", user).andExpect { status { isConflict() } }
	}

	@Test
	fun `redeems a coupon with a differently-cased code`() {
		// given
		createCoupon("WIOSNA").andExpect { status { isCreated() } }

		// expect — code matching is case-insensitive end to end
		redeem("wiosna", UUID.randomUUID()).andExpect { status { isOk() } }
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
