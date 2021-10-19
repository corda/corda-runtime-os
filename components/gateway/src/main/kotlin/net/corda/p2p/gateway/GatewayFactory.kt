package net.corda.p2p.gateway

import com.typesafe.config.Config

interface GatewayFactory {
    fun createGateway(nodeConfiguration: Config): Gateway
}
