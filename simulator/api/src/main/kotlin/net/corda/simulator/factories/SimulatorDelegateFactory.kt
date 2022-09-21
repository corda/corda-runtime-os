package net.corda.simulator.factories

import net.corda.simulator.SimulatedCordaNetwork
import net.corda.simulator.SimulatorConfiguration
import net.corda.v5.base.annotations.DoNotImplement

/**
 * Simulator uses this factory to load a delegate implementation at runtime. This interface should not
 * be used directly; construct a new [net.corda.simulator.Simulator] instead.
 */
@DoNotImplement
interface SimulatorDelegateFactory {

    /**
     * Constructs a new Simulator delegate using the provided configuration or default if none provided.
     *
     * @param configuration The configuration with which to construct [net.corda.simulator.Simulator].
     * @return A [net.corda.simulator.Simulator] delegate.
     */
    fun create(configuration: SimulatorConfiguration): SimulatedCordaNetwork
}
