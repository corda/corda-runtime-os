package net.corda.utilities.time

import java.time.Instant

class TestClock(initialTime: Instant): Clock {

    private var now = initialTime

    override fun instant(): Instant {
        return now
    }

    fun setTime(instant: Instant) {
        now = instant
    }

}