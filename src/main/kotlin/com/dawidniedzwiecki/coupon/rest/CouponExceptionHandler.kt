package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.InvalidValueException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps domain/edge exceptions to RFC 9457 ProblemDetail responses. Validation errors are handled by Spring. */
@RestControllerAdvice
class CouponExceptionHandler {

	@ExceptionHandler(CouponCodeAlreadyExistsException::class)
	fun handleDuplicateCode(ex: CouponCodeAlreadyExistsException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "A coupon with this code already exists")

	/** Fail-closed: if the caller's country can't be verified, the redemption is not attempted. */
	@ExceptionHandler(GeoIpUnavailableException::class)
	fun handleGeoIpUnavailable(ex: GeoIpUnavailableException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify the caller's country")

	/**
	 * A malformed value object (code, country, IP) rejected at construction. Scoped to
	 * InvalidValueException so an unexpected IllegalArgumentException from deeper in the stack
	 * still surfaces as 500 rather than being mislabelled a client error.
	 */
	@ExceptionHandler(InvalidValueException::class)
	fun handleInvalidInput(ex: InvalidValueException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")
}
