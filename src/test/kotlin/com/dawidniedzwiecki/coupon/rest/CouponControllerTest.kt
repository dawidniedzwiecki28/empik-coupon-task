package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

/**
 * Web-layer slice: maps each service outcome and edge case to the right HTTP status and body, with the
 * service behind a stub (no database). Business behaviour lives in CouponOperationsTest.
 */
@WebMvcTest(CouponController::class)
@Import(ClientIpResolver::class, CouponControllerTest.StubConfig::class)
class CouponControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val operations: StubCouponOperations,
) {

	@TestConfiguration
	class StubConfig {
		@Bean
		fun couponOperations(): StubCouponOperations = StubCouponOperations()
	}

	@BeforeEach
	fun reset() = operations.reset()

	// --- create ---

	@Test
	fun `create returns 201 with the new coupon id`() {
		// given
		val id = UUID.randomUUID()
		operations.onCreate = { CouponId(id) }

		// expect
		createRequest("""{"code":"WIOSNA","maxUses":100,"country":"PL"}""").andExpect {
			status { isCreated() }
			jsonPath("$.couponId") { value(id.toString()) }
		}
	}

	@Test
	fun `create rejects a blank code with 400`() {
		createRequest("""{"code":"","maxUses":1,"country":"PL"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `create rejects a non-positive maxUses with 400`() {
		createRequest("""{"code":"X","maxUses":0,"country":"PL"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `create rejects a malformed country with 400`() {
		// passes @NotBlank but CountryCode.of rejects it — handled as 400, not 500
		createRequest("""{"code":"X","maxUses":1,"country":"XYZ"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `create maps a duplicate code to 409`() {
		// given
		operations.onCreate = { throw CouponCodeAlreadyExistsException("WIOSNA") }

		// expect
		createRequest("""{"code":"WIOSNA","maxUses":1,"country":"PL"}""").andExpect { status { isConflict() } }
	}

	// --- redeem: outcome -> status mapping ---

	@Test
	fun `redeem returns 200 on success`() {
		operations.onRedeem = { RedemptionResult.Success }
		redeemRequest().andExpect { status { isOk() } }
	}

	@Test
	fun `redeem maps CouponNotFound to 404`() {
		operations.onRedeem = { RedemptionResult.CouponNotFound }
		redeemRequest().andExpect { status { isNotFound() } }
	}

	@Test
	fun `redeem maps LimitReached to 409`() {
		operations.onRedeem = { RedemptionResult.LimitReached }
		redeemRequest().andExpect { status { isConflict() } }
	}

	@Test
	fun `redeem maps AlreadyRedeemedByUser to 409`() {
		operations.onRedeem = { RedemptionResult.AlreadyRedeemedByUser }
		redeemRequest().andExpect { status { isConflict() } }
	}

	@Test
	fun `redeem maps CountryNotAllowed to 403 with both countries`() {
		operations.onRedeem = { RedemptionResult.CountryNotAllowed(requiredCountry = "PL", callerCountry = "DE") }
		redeemRequest().andExpect {
			status { isForbidden() }
			jsonPath("$.requiredCountry") { value("PL") }
			jsonPath("$.callerCountry") { value("DE") }
		}
	}

	@Test
	fun `redeem maps a geo-IP failure to 503`() {
		operations.onRedeem = { throw GeoIpUnavailableException("1.1.1.1") }
		redeemRequest().andExpect { status { isServiceUnavailable() } }
	}

	// --- redeem: request validation ---

	@Test
	fun `redeem rejects a blank code with 400`() {
		redeemRequest("""{"code":"","userId":"${UUID.randomUUID()}"}""").andExpect { status { isBadRequest() } }
	}

	@Test
	fun `redeem rejects a non-UUID userId with 400`() {
		redeemRequest("""{"code":"WIOSNA","userId":"not-a-uuid"}""").andExpect { status { isBadRequest() } }
	}

	private fun createRequest(body: String) =
		mockMvc.post("/api/coupons") {
			contentType = MediaType.APPLICATION_JSON
			content = body
		}

	private fun redeemRequest(body: String = """{"code":"WIOSNA","userId":"${UUID.randomUUID()}"}""") =
		mockMvc.post("/api/coupons/redemptions") {
			contentType = MediaType.APPLICATION_JSON
			content = body
		}
}

/** Hand-written stand-in so the slice controls outcomes without mocking value-class return types. */
class StubCouponOperations : CouponOperations {
	var onCreate: () -> CouponId = { CouponId(UUID.randomUUID()) }
	var onRedeem: () -> RedemptionResult = { RedemptionResult.Success }

	override fun createCoupon(command: CreateCouponCommand): CouponId = onCreate()

	override fun redeem(command: RedeemCouponCommand): RedemptionResult = onRedeem()

	fun reset() {
		onCreate = { CouponId(UUID.randomUUID()) }
		onRedeem = { RedemptionResult.Success }
	}
}
