package net.corda.utilities.clock

import net.corda.utilities.time.Clock

interface ClockFactory {
    fun createUTCClock(): Clock
}
