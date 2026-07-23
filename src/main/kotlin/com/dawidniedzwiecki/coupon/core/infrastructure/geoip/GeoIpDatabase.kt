package com.dawidniedzwiecki.coupon.core.infrastructure.geoip

import com.maxmind.geoip2.DatabaseReader
import java.util.concurrent.atomic.AtomicReference

/** Holds the active IP→country reader and hot-swaps it atomically; reads are lock-free. */
class GeoIpDatabase(initial: DatabaseReader) : AutoCloseable {

	private val current = AtomicReference(initial)

	fun reader(): DatabaseReader = current.get()

	fun swap(next: DatabaseReader) {
		// Readers are in MEMORY mode (no file handle), so the displaced one is just heap for GC.
		// Not close()d: a concurrent lookup may still be mid-read on it.
		current.set(next)
	}

	override fun close() = current.get().close()
}
