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

	@ExceptionHandler(GeoIpUnavailableException::class)
	fun handleGeoIpUnavailable(ex: GeoIpUnavailableException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Unable to verify the caller's country")

	// Scoped to InvalidValueException so an unrelated IllegalArgumentException deeper in the stack stays a 500, not a 400.
	@ExceptionHandler(InvalidValueException::class)
	fun handleInvalidInput(ex: InvalidValueException): ProblemDetail =
		ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request")
}
