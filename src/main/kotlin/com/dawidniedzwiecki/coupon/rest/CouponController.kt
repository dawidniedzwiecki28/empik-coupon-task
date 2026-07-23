package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponNotFoundException
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CouponView
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import com.dawidniedzwiecki.coupon.rest.dto.CouponResponse
import com.dawidniedzwiecki.coupon.rest.dto.CreateCouponRequest
import com.dawidniedzwiecki.coupon.rest.dto.CreateCouponResponse
import com.dawidniedzwiecki.coupon.rest.dto.RedeemCouponRequest
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping(CouponController.COUPONS_PATH)
class CouponController(
	private val couponOperations: CouponOperations,
	private val clientIpResolver: ClientIpResolver,
) {

	@PostMapping
	fun createCoupon(@Valid @RequestBody request: CreateCouponRequest): ResponseEntity<CreateCouponResponse> {
		val couponId = couponOperations.createCoupon(request.toCommand())
		val location = URI.create("$COUPONS_PATH/${couponId.value}")
		return ResponseEntity.created(location).body(CreateCouponResponse(couponId.value))
	}

	@GetMapping("/{id}")
	fun getCoupon(@PathVariable id: UUID): CouponResponse =
		couponOperations.findCoupon(CouponId(id))?.toResponse() ?: throw CouponNotFoundException(id)

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
				problemResponse(HttpStatus.NOT_FOUND, "coupon-not-found", "No coupon exists for the supplied code")

			RedemptionResult.LimitReached ->
				problemResponse(HttpStatus.CONFLICT, "limit-reached", "The coupon has reached its usage limit")

			RedemptionResult.AlreadyRedeemedByUser ->
				problemResponse(HttpStatus.CONFLICT, "already-redeemed", "This user has already redeemed this coupon")

			is RedemptionResult.CountryNotAllowed ->
				problemResponse(HttpStatus.FORBIDDEN, "country-not-allowed", "The coupon is not available in the caller's country") {
					setProperty("requiredCountry", requiredCountry)
					setProperty("callerCountry", callerCountry)
				}
		}

	// A distinct `type` URI per rejection so a machine can tell the two 409s apart without parsing `detail`.
	private fun problemResponse(
		status: HttpStatus,
		type: String,
		detail: String,
		addProperties: ProblemDetail.() -> Unit = {},
	): ResponseEntity<ProblemDetail> {
		val problemDetail = ProblemDetail.forStatusAndDetail(status, detail).apply {
			this.type = problemType(type)
			addProperties()
		}
		return ResponseEntity.status(status).body(problemDetail)
	}

	private fun CouponView.toResponse(): CouponResponse =
		CouponResponse(id, code, country, maxUses, currentUses, createdAt)

	private fun CreateCouponRequest.toCommand(): CreateCouponCommand =
		CreateCouponCommand(CouponCode.of(code), maxUses, CountryCode.of(country))

	private fun RedeemCouponRequest.toCommand(clientIp: IpAddress): RedeemCouponCommand =
		RedeemCouponCommand(CouponCode.of(code), UserId(userId), clientIp)

	companion object {
		const val COUPONS_PATH = "/api/coupons"
		const val REDEMPTIONS_PATH = "/redemptions"
	}
}
