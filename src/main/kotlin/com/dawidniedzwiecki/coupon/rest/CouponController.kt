package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.IpAddress
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
@RequestMapping(CouponController.COUPONS_PATH)
class CouponController(
	private val couponOperations: CouponOperations,
	private val clientIpResolver: ClientIpResolver,
) {

	@PostMapping
	fun createCoupon(@Valid @RequestBody request: CreateCouponRequest): ResponseEntity<CreateCouponResponse> {
		val couponId = couponOperations.createCoupon(request.toCommand())
		return ResponseEntity.status(HttpStatus.CREATED).body(CreateCouponResponse(couponId.value))
	}

	@PostMapping(REDEMPTIONS_PATH)
	fun redeemCoupon(
		@Valid @RequestBody request: RedeemCouponRequest,
		httpRequest: HttpServletRequest,
	): ResponseEntity<ProblemDetail> {
		val clientIp = clientIpResolver.resolve(httpRequest)
		return couponOperations.redeem(request.toCommand(clientIp)).toResponseEntity()
	}

	/** Rejections carry an RFC 9457 [ProblemDetail]; success is a body-less 200 — hence the ProblemDetail type. */
	private fun RedemptionResult.toResponseEntity(): ResponseEntity<ProblemDetail> =
		when (this) {
			RedemptionResult.Success ->
				ResponseEntity.ok().build()

			RedemptionResult.CouponNotFound ->
				problemResponse(HttpStatus.NOT_FOUND, "No coupon exists for the supplied code")

			RedemptionResult.LimitReached ->
				problemResponse(HttpStatus.CONFLICT, "The coupon has reached its usage limit")

			RedemptionResult.AlreadyRedeemedByUser ->
				problemResponse(HttpStatus.CONFLICT, "This user has already redeemed this coupon")

			is RedemptionResult.CountryNotAllowed ->
				problemResponse(HttpStatus.FORBIDDEN, "The coupon is not available in the caller's country") {
					setProperty("requiredCountry", requiredCountry)
					setProperty("callerCountry", callerCountry)
				}
		}

	private fun problemResponse(
		status: HttpStatus,
		detail: String,
		addProperties: ProblemDetail.() -> Unit = {},
	): ResponseEntity<ProblemDetail> {
		val problemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply(addProperties)
		return ResponseEntity.status(status).body(problemDetail)
	}

	private fun CreateCouponRequest.toCommand(): CreateCouponCommand =
		CreateCouponCommand(CouponCode.of(code), maxUses, CountryCode.of(country))

	private fun RedeemCouponRequest.toCommand(clientIp: IpAddress): RedeemCouponCommand =
		RedeemCouponCommand(CouponCode.of(code), UserId(userId), clientIp)

	companion object {
		const val COUPONS_PATH = "/api/coupons"
		const val REDEMPTIONS_PATH = "/redemptions"
	}
}
