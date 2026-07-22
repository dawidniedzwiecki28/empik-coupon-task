# Coupon Service

A REST service for managing discount coupons and registering their redemption, built for a
recruitment task. Kotlin · Spring Boot · PostgreSQL.

> This README is a stub; it is expanded (architecture, run instructions, design rationale,
> scaling notes) in the final delivery PR.

## Features

- Create a coupon (unique case-insensitive code, max uses, target country).
- Redeem a coupon for a user, enforcing:
  - usage limit ("first come, first served") under concurrency,
  - one redemption per user per coupon,
  - country restriction based on the caller's IP,
  - clear, distinct outcomes for every rejection case.

## Tech

Kotlin, Spring Boot, PostgreSQL, Flyway, JPA + JDBC, Caffeine, JUnit 5, Testcontainers,
WireMock. Built with Gradle, targeting Java 21.
