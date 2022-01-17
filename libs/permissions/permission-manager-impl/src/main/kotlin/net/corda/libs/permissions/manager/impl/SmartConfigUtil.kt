package net.corda.libs.permissions.manager.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.Companion.CONFIG_RPC_TIMEOUT_MILLIS
import java.time.Duration

internal object SmartConfigUtil {
    private const val DEFAULT_ENDPOINT_TIMEOUT_MS = 10000L

    fun SmartConfig.getEndpointTimeout(): Duration {
        return if (hasPath(CONFIG_RPC_TIMEOUT_MILLIS)) {
            Duration.ofMillis(getLong(CONFIG_RPC_TIMEOUT_MILLIS))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }
}