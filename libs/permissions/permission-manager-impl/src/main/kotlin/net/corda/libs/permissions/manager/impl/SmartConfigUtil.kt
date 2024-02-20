package net.corda.libs.permissions.manager.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.schema.configuration.ConfigKeys.REST_ENDPOINT_TIMEOUT_MILLIS
import net.corda.utilities.seconds
import java.time.Duration

internal object SmartConfigUtil {
    private val DEFAULT_ENDPOINT_TIMEOUT_MS = 30.seconds.toMillis()

    fun SmartConfig.getEndpointTimeout(): Duration {
        return if (hasPath(REST_ENDPOINT_TIMEOUT_MILLIS)) {
            Duration.ofMillis(getLong(REST_ENDPOINT_TIMEOUT_MILLIS))
        } else {
            Duration.ofMillis(DEFAULT_ENDPOINT_TIMEOUT_MS)
        }
    }
}
