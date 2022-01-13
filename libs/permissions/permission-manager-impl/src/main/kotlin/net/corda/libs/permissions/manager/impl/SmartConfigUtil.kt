package net.corda.libs.permissions.manager.impl

import net.corda.libs.configuration.SmartConfig
import java.time.Duration

internal object SmartConfigUtil {
    private const val ENDPOINT_TIMEOUT_PATH = "endpointTimeoutMs"
    private const val DEFAULT_ENDPOINT_TIMEOUT_MS = 10000L

    fun SmartConfig.getEndpointTimeout(): Duration {
        return if (hasPath(ENDPOINT_TIMEOUT_PATH)) {
            Duration.ofMillis(getLong(ENDPOINT_TIMEOUT_PATH))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }
}