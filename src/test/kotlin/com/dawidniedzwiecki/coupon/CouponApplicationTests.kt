package com.dawidniedzwiecki.coupon

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest
class CouponApplicationTests {

	@Test
	fun contextLoads() {
		// Verifies the Spring context (beans, JPA, Flyway) starts successfully.
	}
}
