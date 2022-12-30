package net.corda.membership.lib.p2p

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys
import net.corda.v5.base.exceptions.CordaRuntimeException

enum class TlsType(
    val groupPolicyName: String,
) {
    ONE_WAY("OneWay"),
    MUTUAL("Mutual");

    companion object {
        fun getClusterType(configurationGetter: (String)-> SmartConfig?) : TlsType {
            val gatewayConfiguration =
                configurationGetter.invoke(ConfigKeys.P2P_GATEWAY_CONFIG) ?:
                throw FailToReadClusterTlsTypeException(
                    "Could not get the Gateway configuration"
                )
            val tlsType = gatewayConfiguration
                .getConfig("sslConfig")
                .getString("tlsType")
            return valueOf(tlsType)
        }
    }

    class FailToReadClusterTlsTypeException(message: String)  : CordaRuntimeException(message)
}
