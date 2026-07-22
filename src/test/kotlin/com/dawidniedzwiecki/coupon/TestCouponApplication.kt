package com.dawidniedzwiecki.coupon

import org.springframework.boot.fromApplication
import org.springframework.boot.with


fun main(args: Array<String>) {
	fromApplication<CouponApplication>().with(TestcontainersConfiguration::class).run(*args)
}
