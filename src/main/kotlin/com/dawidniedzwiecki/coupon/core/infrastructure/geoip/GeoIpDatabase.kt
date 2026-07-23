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
		// Every reader is loaded in MEMORY mode (no file handle/mmap), so the displaced one holds only
		// heap and is reclaimed by GC. It is intentionally not close()d here: a concurrent lookup may
		// still be mid-read on it during the swap, and closing would break that lookup.
		current.set(next)
	}

	override fun close() = current.get().close()
}
