package net.corda.libs.permissions.manager.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS
import java.time.Duration

internal object SmartConfigUtil {
    private const val DEFAULT_ENDPOINT_TIMEOUT_MS = 30000L

    fun SmartConfig.getEndpointTimeout(): Duration {
        return if (hasPath(REST_ENDPOINT_TIMEOUT_MILLIS)) {
            Duration.ofMillis(getLong(REST_ENDPOINT_TIMEOUT_MILLIS))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }
}