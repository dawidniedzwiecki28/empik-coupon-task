package com.dawidniedzwiecki.coupon.rest

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CouponCode
import com.dawidniedzwiecki.coupon.core.api.CouponCodeAlreadyExistsException
import com.dawidniedzwiecki.coupon.core.api.CouponId
import com.dawidniedzwiecki.coupon.core.api.CouponOperations
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.GeoIpUnavailableException
import com.dawidniedzwiecki.coupon.core.api.IpAddress
import com.dawidniedzwiecki.coupon.core.api.RedeemCouponCommand
import com.dawidniedzwiecki.coupon.core.api.RedemptionResult
import com.dawidniedzwiecki.coupon.core.api.UserId
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
 * Web-layer slice: maps each service outcome, edge case and request to the right HTTP status/body and
 * core command, service mocked. Business behaviour lives in CouponOperationsTest.
 */
@WebMvcTest(CouponController::class)
@Import(ClientIpResolver::class, CouponControllerTest.MockConfig::class)
class CouponControllerTest @Autowired constructor(
	private val mockMvc: MockMvc,
	private val operations: CouponOperations,
) {

	@TestConfiguration
	class MockConfig {
		@Bean
		fun couponOperations(): CouponOperations = mockk()
	}

	@BeforeEach
	fun reset() = clearMocks(operations)

	// --- create ---

	@Test
	fun `create returns 201 with the new id and maps the request to a command`() {
		// given
		val id = UUID.randomUUID()
		every { operations.createCoupon(any()) } returns CouponId(id)

		// when
		val result = createRequest("""{"code":"wiosna","maxUses":100,"country":"pl"}""")

		// then
		result.andExpect {
			status { isCreated() }
			jsonPath("$.couponId") { value(id.toString()) }
		}
		verify { operations.createCoupon(CreateCouponCommand(CouponCode.of("WIOSNA"), 100, CountryCode.of("PL"))) }
	}

	@Test
	fun `create rejects a blank code with a 400 problem`() {
		// expect
		createRequest("""{"code":"","maxUses":1,"country":"PL"}""").andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `create rejects a non-positive maxUses with a 400 problem`() {
		// expect
		createRequest("""{"code":"X","maxUses":0,"country":"PL"}""").andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `create rejects a malformed country with a 400 problem`() {
		// expect — CountryCode.of rejects it, mapped to 400 not 500
		createRequest("""{"code":"X","maxUses":1,"country":"XYZ"}""").andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `create maps a duplicate code to a 409 problem`() {
		// given
		every { operations.createCoupon(any()) } throws CouponCodeAlreadyExistsException("WIOSNA")

		// expect
		createRequest("""{"code":"WIOSNA","maxUses":1,"country":"PL"}""").andExpect {
			status { isConflict() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	// --- redeem: outcome -> status mapping ---

	@Test
	fun `redeem returns 200 and maps the request to a command`() {
		// given
		val userId = UUID.randomUUID()
		every { operations.redeem(any()) } returns RedemptionResult.Success

		// when
		val result = redeemRequest("""{"code":"wiosna","userId":"$userId"}""")

		// then — code normalized, IP resolved from the remote address
		result.andExpect { status { isOk() } }
		verify {
			operations.redeem(RedeemCouponCommand(CouponCode.of("WIOSNA"), UserId(userId), IpAddress.of("127.0.0.1")))
		}
	}

	@Test
	fun `redeem maps CouponNotFound to a 404 problem`() {
		// given
		every { operations.redeem(any()) } returns RedemptionResult.CouponNotFound

		// expect
		redeemRequest().andExpect {
			status { isNotFound() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `redeem maps LimitReached to a 409 problem`() {
		// given
		every { operations.redeem(any()) } returns RedemptionResult.LimitReached

		// expect
		redeemRequest().andExpect {
			status { isConflict() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `redeem maps AlreadyRedeemedByUser to a 409 problem`() {
		// given
		every { operations.redeem(any()) } returns RedemptionResult.AlreadyRedeemedByUser

		// expect
		redeemRequest().andExpect {
			status { isConflict() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `redeem maps CountryNotAllowed to a 403 problem with both countries`() {
		// given
		every { operations.redeem(any()) } returns RedemptionResult.CountryNotAllowed(requiredCountry = "PL", callerCountry = "DE")

		// expect
		redeemRequest().andExpect {
			status { isForbidden() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
			jsonPath("$.requiredCountry") { value("PL") }
			jsonPath("$.callerCountry") { value("DE") }
		}
	}

	@Test
	fun `redeem maps a geo-IP failure to a 503 problem`() {
		// given
		every { operations.redeem(any()) } throws GeoIpUnavailableException("1.1.1.1")

		// expect
		redeemRequest().andExpect {
			status { isServiceUnavailable() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	// --- redeem: request validation ---

	@Test
	fun `redeem rejects a blank code with a 400 problem`() {
		// expect
		redeemRequest("""{"code":"","userId":"${UUID.randomUUID()}"}""").andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
	}

	@Test
	fun `redeem rejects a non-UUID userId with a 400 problem`() {
		// expect
		redeemRequest("""{"code":"WIOSNA","userId":"not-a-uuid"}""").andExpect {
			status { isBadRequest() }
			content { contentType(MediaType.APPLICATION_PROBLEM_JSON) }
		}
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
