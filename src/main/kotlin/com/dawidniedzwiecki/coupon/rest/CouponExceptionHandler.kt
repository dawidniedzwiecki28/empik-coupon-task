package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponNotFoundException
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.InvalidValueException
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Maps domain/edge exceptions to RFC 9457 ProblemDetail responses. Validation errors are handled by Spring. */
@RestControllerAdvice
class CouponExceptionHandler {

	@ExceptionHandler(CouponCodeAlreadyExistsException::class)
	fun handleDuplicateCode(ex: CouponCodeAlreadyExistsException): ProblemDetail =
		problem(HttpStatus.CONFLICT, "code-already-exists", "A coupon with this code already exists")

	@ExceptionHandler(CouponNotFoundException::class)
	fun handleCouponNotFound(ex: CouponNotFoundException): ProblemDetail =
		problem(HttpStatus.NOT_FOUND, "coupon-not-found", "No coupon exists with the supplied id")

	// 422, not 503: the caller's IP simply can't be mapped to a country (fail-closed) — a property of the
	// request we can't process, not a server outage. The bundled database is always available in-process.
	@ExceptionHandler(GeoIpUnavailableException::class)
	fun handleGeoIpUnavailable(ex: GeoIpUnavailableException): ProblemDetail =
		problem(HttpStatus.UNPROCESSABLE_ENTITY, "country-unresolved", "Unable to verify the caller's country")

	// Scoped to InvalidValueException so an unrelated IllegalArgumentException deeper in the stack stays a 500, not a 400.
	@ExceptionHandler(InvalidValueException::class)
	fun handleInvalidInput(ex: InvalidValueException): ProblemDetail =
		problem(HttpStatus.BAD_REQUEST, "invalid-request", ex.message ?: "Invalid request")

	private fun problem(status: HttpStatusCode, type: String, detail: String): ProblemDetail =
		ProblemDetail.forStatusAndDetail(status, detail).apply { this.type = problemType(type) }
}
