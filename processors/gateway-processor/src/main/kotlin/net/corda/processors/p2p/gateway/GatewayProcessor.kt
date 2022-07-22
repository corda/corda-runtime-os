package net.corda.processors.p2p.gateway

import net.corda.libs.configuration.SmartConfig

/** The processor for a `FlowWorker`. */
interface GatewayProcessor {
    fun start(bootConfig: SmartConfig, useStubComponents: Boolean)

    fun stop()
}