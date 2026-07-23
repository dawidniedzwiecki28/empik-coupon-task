package com.dawidniedzwiecki.coupon.rest

import java.net.URI

/**
 * RFC 9457 `type` URIs for this API's problems. A URN (not a resolvable URL) - a stable machine-readable
 * tag that distinguishes outcomes sharing a status (e.g. the two 409s) without promising a docs page.
 */
fun problemType(slug: String): URI = URI.create("urn:coupon:$slug")
