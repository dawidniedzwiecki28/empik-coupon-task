package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import com.dawidniedzwiecki.coupon.core.api.InvalidValueException
import jakarta.annotation.Nonnull
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
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
	// Provider-generated: the id is null until persist, so Spring Data's isNew() picks persist() over
	// merge() — a plain INSERT with no SELECT-before-INSERT round trip.
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	val id: UUID? = null,
	@Nonnull
	val code: String,
	@Nonnull
	val createdAt: Instant,
	val maxUses: Int,
	val currentUses: Int,
	// CHAR(2) column — mapped explicitly so Hibernate validation matches.
	@Nonnull
	@JdbcTypeCode(SqlTypes.CHAR)
	val country: String,
) {
	companion object {
		/** Unique constraint on `code`; must match `uq_coupons_code` in V1__create_coupons.sql. */
		const val UNIQUE_CODE_CONSTRAINT = "uq_coupons_code"

		fun create(command: CreateCouponCommand, clock: Clock): CouponEntity {
			if (command.maxUses <= 0) throw InvalidValueException("maxUses must be positive")
			return CouponEntity(
				code = command.code.value,
				createdAt = Instant.now(clock),
				maxUses = command.maxUses,
				currentUses = 0,
				country = command.country.value,
			)
		}
	}
}
