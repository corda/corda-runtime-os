package net.corda.processors.p2p.gateway

import net.corda.libs.configuration.SmartConfig
import net.corda.p2p.gateway.Gateway

/** The processor for a [Gateway]. */
interface GatewayProcessor {
    fun start(bootConfig: SmartConfig)

    fun stop()
}