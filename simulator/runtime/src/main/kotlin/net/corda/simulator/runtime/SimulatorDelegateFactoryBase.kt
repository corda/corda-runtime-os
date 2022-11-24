package net.corda.simulator.runtime

import net.corda.simulator.SimulatedCordaNetwork
import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.factories.SimulatorDelegateFactory

/**
 * @see SimulatorDelegateFactory for details.
 */
class SimulatorDelegateFactoryBase : SimulatorDelegateFactory {
    override fun create(configuration: SimulatorConfiguration): SimulatedCordaNetwork {
        return SimulatorDelegateBase(configuration)
    }
}