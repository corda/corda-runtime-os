package net.corda.test.util.time

import net.corda.utilities.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * A test clock which automatically ticks up the clock by the specified [autoTickBy] amount prior
 * to returning an instant.
 */
class AutoTickTestClock(initialTime: Instant, private val autoTickBy: Duration) : Clock {

    private var now = initialTime

    override fun instant(): Instant {
        now += autoTickBy
        return now
    }

    /**
     * Returns the current time of the clock without triggering auto-ticking
     */
    fun peekTime(): Instant = now

    fun setTime(instant: Instant) {
        now = instant
    }
}
