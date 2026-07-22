package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import com.dawidniedzwiecki.coupon.core.api.CreateCouponCommand
import jakarta.annotation.Nonnull
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
	@Nonnull
	val id: UUID,
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
		/** Builds a new coupon from a create command, enforcing its construction invariants. */
		fun create(command: CreateCouponCommand, clock: Clock): CouponEntity {
			require(command.maxUses > 0) { "maxUses must be positive" }
			return CouponEntity(
				id = UUID.randomUUID(),
				code = command.code.value,
				createdAt = Instant.now(clock),
				maxUses = command.maxUses,
				currentUses = 0,
				country = command.country.value,
			)
		}
	}
}
