package com.dawidniedzwiecki.coupon.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfiguration {

	@Bean
	fun couponServiceOpenApi(): OpenAPI =
		OpenAPI().info(
			Info()
				.title("Coupon Service API")
				.version("v1")
				.description("Create discount coupons and register their redemption."),
		)
}
