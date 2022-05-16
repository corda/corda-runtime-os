package net.corda.utilities.time

import java.time.Instant

class UTCClock: Clock {
    private val clock = java.time.Clock.systemUTC()

    override fun instant(): Instant {
        return clock.instant()
    }

}