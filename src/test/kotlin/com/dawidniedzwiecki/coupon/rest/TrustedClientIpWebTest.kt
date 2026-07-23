package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * With client-IP trust enabled, a malformed `X-Forwarded-For` must surface as a 400 problem+json — proving
 * the resolver runs inside the controller (where @RestControllerAdvice maps its InvalidValueException),
 * not in a filter that would leak a 500. Separate slice because trust is off in [CouponControllerTest].
 */
@WebMvcTest(CouponController::class)
@Import(ClientIpResolver::class, TrustedClientIpWebTest.MockConfig::class)
@TestPropertySource(properties = ["coupon.rest.trust-client-ip=true"])
class TrustedClientIpWebTest @Autowired constructor(
	private val mockMvc: MockMvc,
) {

	@TestConfiguration
	class MockConfig {
		@Bean
		fun couponOperations(): CouponOperations = mockk()
	}

	@Test
	fun `redeem with a malformed X-Forwarded-For returns a 400 problem, not a 500`() {
		// expect — resolver throws before the service is touched, and the advice maps it to 400
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			headers { add("X-Forwarded-For", "not-an-ip") }
			content = """{"code":"WIOSNA","userId":"${UUID.randomUUID()}"}"""
		}.andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}
}
