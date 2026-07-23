package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import com.dawidniedzwiecki.coupon.rest.dto.CreateCouponRequest
import com.dawidniedzwiecki.coupon.rest.dto.CreateCouponResponse
import com.dawidniedzwiecki.coupon.rest.dto.RedeemCouponRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/coupons")
class CouponController(
	private val couponOperations: CouponOperations,
	private val clientIpResolver: ClientIpResolver,
) {

	@PostMapping
	fun create(@Valid @RequestBody request: CreateCouponRequest): ResponseEntity<CreateCouponResponse> {
		val couponId = couponOperations.createCoupon(
			CreateCouponCommand(CouponCode.of(request.code), request.maxUses, CountryCode.of(request.country)),
		)
		return ResponseEntity.status(HttpStatus.CREATED).body(CreateCouponResponse(couponId.value))
	}

	@PostMapping("/redemptions")
	fun redeem(@Valid @RequestBody request: RedeemCouponRequest, httpRequest: HttpServletRequest): ResponseEntity<Any> {
		val command = RedeemCouponCommand(
			code = CouponCode.of(request.code),
			userId = UserId(request.userId),
			clientIp = clientIpResolver.resolve(request.ipOverride, httpRequest),
		)
		return when (val result = couponOperations.redeem(command)) {
			RedemptionResult.Success -> ResponseEntity.ok().build()
			RedemptionResult.CouponNotFound ->
				problem(HttpStatus.NOT_FOUND, "No coupon exists for the supplied code")
			RedemptionResult.LimitReached ->
				problem(HttpStatus.CONFLICT, "The coupon has reached its usage limit")
			RedemptionResult.AlreadyRedeemedByUser ->
				problem(HttpStatus.CONFLICT, "This user has already redeemed the coupon")
			is RedemptionResult.CountryNotAllowed ->
				problem(
					HttpStatus.FORBIDDEN,
					"The coupon is not available in your country",
					mapOf("requiredCountry" to result.requiredCountry, "callerCountry" to result.callerCountry),
				)
		}
	}

	private fun problem(status: HttpStatus, detail: String, properties: Map<String, Any> = emptyMap()): ResponseEntity<Any> {
		val body = ProblemDetail.forStatusAndDetail(status, detail)
		properties.forEach(body::setProperty)
		return ResponseEntity.status(status).body(body)
	}
}
