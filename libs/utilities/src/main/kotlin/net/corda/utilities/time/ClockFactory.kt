package net.corda.utilities.time

interface ClockFactory {
    fun createUTCClock(): Clock
}
