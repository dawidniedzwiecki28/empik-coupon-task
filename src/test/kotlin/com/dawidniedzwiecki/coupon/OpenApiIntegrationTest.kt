package com.dawidniedzwiecki.coupon

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class OpenApiIntegrationTest @Autowired constructor(
	private val mockMvc: MockMvc,
) {

	@Test
	fun `serves an OpenAPI document describing the coupon endpoints`() {
		// expect
		mockMvc.get("/v3/api-docs").andExpect {
			status { isOk() }
			jsonPath("$.info.title") { value("Coupon Service API") }
			jsonPath("$.paths['/api/coupons'].post") { exists() }
			jsonPath("$.paths['/api/coupons/redemptions'].post") { exists() }
		}
	}
}
