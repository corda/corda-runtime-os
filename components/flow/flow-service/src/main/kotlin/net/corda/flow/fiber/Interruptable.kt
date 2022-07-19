package net.corda.flow.fiber

interface Interruptable {
    fun attemptInterrupt()
}
