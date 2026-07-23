package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.maxmind.geoip2.DatabaseReader
import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the active IP→country [DatabaseReader] and lets it be hot-swapped when a fresher database is
 * fetched. Reads are lock-free; [swap] atomically publishes a new reader so in-flight lookups keep
 * using the old one.
 */
class GeoIpDatabase(initial: DatabaseReader) : AutoCloseable {

	private val current = AtomicReference(initial)

	fun reader(): DatabaseReader = current.get()

	fun swap(next: DatabaseReader) {
		// The replaced reader is in-memory (no OS handle) and is reclaimed by GC once the last
		// concurrent lookup releases it; closing it here could break a lookup mid-swap.
		current.set(next)
	}

	override fun close() = current.get().close()
}
