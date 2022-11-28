package net.corda.membership.p2p.helpers

import net.corda.configuration.read.ConfigChangedEvent
import net.corda.libs.configuration.helper.getConfig
import net.corda.schema.configuration.ConfigKeys

enum class TlsType {
    MUTUAL,
    ONE_WAY;

    companion object {
        fun ConfigChangedEvent.tlsType(): TlsType =
            this.config
                .getConfig(ConfigKeys.P2P_GATEWAY_CONFIG)
                .getEnum(TlsType::class.java, "sslConfig.tlsType")
    }
}
