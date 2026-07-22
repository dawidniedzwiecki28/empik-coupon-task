package com.dawidniedzwiecki.coupon.core.infrastructure.persistence

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "coupons")
class CouponEntity(
	@Id
	var id: UUID,
	var code: String,
	var createdAt: Instant,
	var maxUses: Int,
	var currentUses: Int,
	// CHAR(2) column — mapped explicitly so Hibernate validation matches.
	@JdbcTypeCode(SqlTypes.CHAR)
	var country: String,
) {
	companion object {
		fun normalizeCode(raw: String): String = raw.trim().uppercase()
	}
}
