package net.corda.test.util.time

import java.time.Instant
import java.time.temporal.ChronoUnit

fun Instant.toSafeWindowsPrecision(): Instant = this.truncatedTo(ChronoUnit.MILLIS)