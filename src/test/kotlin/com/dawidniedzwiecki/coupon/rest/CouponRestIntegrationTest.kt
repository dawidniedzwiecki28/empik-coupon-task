package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.TestcontainersConfiguration
import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRedemptionRepository
import com.dawidniedzwiecki.coupon.core.infrastructure.persistence.CouponRepository
import com.github.benmanes.caffeine.cache.Cache
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CouponRestIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val couponRepository: CouponRepository,
	private val redemptionRepository: CouponRedemptionRepository,
	private val geoIpCache: Cache<String, CountryCode>,
) {

	companion object {
		private val geoIp = WireMockServer(options().dynamicPort()).apply { start() }

		@JvmStatic
		@AfterAll
		fun stopGeoIp() = geoIp.stop()

		@JvmStatic
		@DynamicPropertySource
		fun geoIpProperties(registry: DynamicPropertyRegistry) {
			registry.add("geoip.base-url") { geoIp.baseUrl() }
			// The suite drives country via ipOverride / X-Forwarded-For, so it runs as if behind a trusted proxy.
			registry.add("coupon.rest.trust-client-ip") { true }
		}
	}

	@BeforeEach
	fun reset() {
		redemptionRepository.deleteAll()
		couponRepository.deleteAll()
		geoIp.resetAll()
		geoIpCache.invalidateAll()
	}

	@Test
	fun `creates a coupon and returns 201 with its id`() {
		// expect
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"WIOSNA","maxUses":3,"country":"PL"}"""
		}.andExpect {
			status { isCreated() }
			jsonPath("$.couponId") { exists() }
		}
	}

	@Test
	fun `rejects an invalid create request with 400`() {
		// expect
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"","maxUses":0,"country":"PL"}"""
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `rejects a duplicate code with 409`() {
		// given
		createCoupon(code = "SUMMER", maxUses = 1, country = "PL")

		// expect
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"summer","maxUses":1,"country":"PL"}"""
		}.andExpect { status { isConflict() } }
	}

	@Test
	fun `redeems a coupon successfully`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		stubCountry(ip = "1.1.1.1", country = "PL")

		// expect
		redeem(code = "WIOSNA", userId = UUID.randomUUID(), ip = "1.1.1.1").andExpect { status { isOk() } }
	}

	@Test
	fun `returns 404 for an unknown coupon`() {
		// given
		stubCountry(ip = "1.1.1.2", country = "PL")

		// expect
		redeem(code = "NOPE", userId = UUID.randomUUID(), ip = "1.1.1.2").andExpect { status { isNotFound() } }
	}

	@Test
	fun `returns 403 when the caller country is not allowed`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		stubCountry(ip = "2.2.2.2", country = "DE")

		// expect
		redeem(code = "WIOSNA", userId = UUID.randomUUID(), ip = "2.2.2.2").andExpect {
			status { isForbidden() }
			jsonPath("$.requiredCountry") { value("PL") }
			jsonPath("$.callerCountry") { value("DE") }
		}
	}

	@Test
	fun `returns 409 when the same user redeems twice`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		stubCountry(ip = "3.3.3.3", country = "PL")
		val user = UUID.randomUUID()

		// when
		val first = redeem(code = "WIOSNA", userId = user, ip = "3.3.3.3")
		val second = redeem(code = "WIOSNA", userId = user, ip = "3.3.3.3")

		// then
		first.andExpect { status { isOk() } }
		second.andExpect { status { isConflict() } }
	}

	@Test
	fun `returns 409 when the usage limit is reached`() {
		// given
		createCoupon(code = "ONCE", maxUses = 1, country = "PL")
		stubCountry(ip = "4.4.4.4", country = "PL")

		// when
		val first = redeem(code = "ONCE", userId = UUID.randomUUID(), ip = "4.4.4.4")
		val second = redeem(code = "ONCE", userId = UUID.randomUUID(), ip = "4.4.4.4")

		// then
		first.andExpect { status { isOk() } }
		second.andExpect { status { isConflict() } }
	}

	@Test
	fun `exactly maxUses redemptions succeed under concurrent load`() {
		// given
		val maxUses = 5
		val attempts = 20
		createCoupon(code = "RUSH", maxUses = maxUses, country = "PL")
		stubCountry(ip = "8.8.4.4", country = "PL")

		// when — distinct users race for the limited slots
		val pool = Executors.newFixedThreadPool(attempts)
		val statuses = try {
			(1..attempts)
				.map { pool.submit<Int> { redeem("RUSH", UUID.randomUUID(), "8.8.4.4").andReturn().response.status } }
				.map { it.get() }
		} finally {
			pool.shutdown()
		}

		// then — the atomic increment lets through exactly maxUses; the rest are rejected as full
		assertEquals(maxUses, statuses.count { it == 200 })
		assertEquals(attempts - maxUses, statuses.count { it == 409 })
		val couponId = couponRepository.findByCode("RUSH")!!.id
		assertEquals(maxUses.toLong(), redemptionRepository.countByIdCouponId(couponId))
	}

	@Test
	fun `returns 503 when geo-IP is unavailable`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		geoIp.stubFor(get(urlPathEqualTo("/5.5.5.5/country/")).willReturn(aResponse().withStatus(500)))

		// expect
		redeem(code = "WIOSNA", userId = UUID.randomUUID(), ip = "5.5.5.5")
			.andExpect { status { isServiceUnavailable() } }
	}

	@Test
	fun `rejects a create with a malformed country with 400`() {
		// expect — passes @NotBlank but CountryCode.of rejects it (handled as 400)
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"WIOSNA","maxUses":3,"country":"XYZ"}"""
		}.andExpect { status { isBadRequest() } }
	}

	@Test
	fun `resolves the caller IP from the X-Forwarded-For header`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		stubCountry(ip = "9.9.9.9", country = "PL")

		// expect — first hop of X-Forwarded-For is used when no override is supplied
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			headers { add("X-Forwarded-For", "9.9.9.9, 10.0.0.1") }
			content = """{"code":"WIOSNA","userId":"${UUID.randomUUID()}"}"""
		}.andExpect { status { isOk() } }
	}

	@Test
	fun `falls back to the remote address when no IP is supplied`() {
		// given
		createCoupon(code = "WIOSNA", maxUses = 3, country = "PL")
		stubCountry(ip = "127.0.0.1", country = "PL") // MockMvc's default remote address

		// expect
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"WIOSNA","userId":"${UUID.randomUUID()}"}"""
		}.andExpect { status { isOk() } }
	}

	private fun createCoupon(code: String, maxUses: Int, country: String) {
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"$code","maxUses":$maxUses,"country":"$country"}"""
		}.andExpect { status { isCreated() } }
	}

	private fun redeem(code: String, userId: UUID, ip: String) =
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			content = """{"code":"$code","userId":"$userId","ipOverride":"$ip"}"""
		}

	private fun stubCountry(ip: String, country: String) {
		geoIp.stubFor(get(urlPathEqualTo("/$ip/country/")).willReturn(aResponse().withStatus(200).withBody(country)))
	}
}
