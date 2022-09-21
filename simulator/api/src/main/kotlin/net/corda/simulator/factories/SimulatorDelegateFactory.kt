package net.corda.simulator.factories

import net.corda.simulator.SimulatedCordaNetwork
import net.corda.simulator.SimulatorConfiguration

interface SimulatorDelegateFactory {
    fun create(configuration: SimulatorConfiguration): SimulatedCordaNetwork
}
