package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.core.api.CountryCode
import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Clock
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coupons")
class CouponEntity(
	@Id
	val id: UUID,
	@Column(nullable = false)
	val code: String,
	@Column(nullable = false)
	val createdAt: Instant,
	@Column(nullable = false)
	val maxUses: Int,
	@Column(nullable = false)
	val currentUses: Int,
	// CHAR(2) column — mapped explicitly so Hibernate validation matches.
	@Column(nullable = false)
	@JdbcTypeCode(SqlTypes.CHAR)
	val country: String,
) {
	companion object {
		fun normalizeCode(raw: String): String = raw.trim().uppercase()

		/** Builds a new coupon from a create command, enforcing its construction invariants. */
		fun create(command: CreateCouponCommand, clock: Clock): CouponEntity {
			require(command.maxUses > 0) { "maxUses must be positive" }
			return CouponEntity(
				id = UUID.randomUUID(),
				code = normalizeCode(command.code),
				createdAt = Instant.now(clock),
				maxUses = command.maxUses,
				currentUses = 0,
				country = CountryCode.of(command.country).value,
			)
		}
	}
}
