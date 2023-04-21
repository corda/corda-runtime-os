package net.corda.utilities.time

import java.time.Instant

interface Clock {
    fun instant(): Instant
}