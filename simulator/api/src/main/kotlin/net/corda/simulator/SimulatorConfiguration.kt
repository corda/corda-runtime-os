package net.corda.simulator

import java.time.Clock
import java.time.Duration

interface SimulatorConfiguration {

    val pollInterval: Duration
    val timeout: Duration
    val clock : Clock

}
