package net.corda.test.util.time

import net.corda.utilities.time.Clock
import java.time.Instant

open class TestClock(initialTime: Instant): Clock {

    protected var now = initialTime

    override fun instant(): Instant {
        return now
    }

    fun setTime(instant: Instant) {
        now = instant
    }
}
