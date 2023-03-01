package net.corda.rest.server.config.models

import net.corda.utilities.NetworkHostAndPort

data class RestServerSettings(
    val address: NetworkHostAndPort,
    val context: RestContext,
    val ssl: RestSSLSettings?,
    val sso: SsoSettings? = null,
    /**
     * The maximum content length in bytes accepted for POST requests
     */
    val maxContentLength: Int,
    /**
     * The time (in milliseconds) after which an idle websocket connection will be timed out and closed
     */
    val webSocketIdleTimeoutMs: Long
) {
    companion object {
        const val MAX_CONTENT_LENGTH_DEFAULT_VALUE = 1024 * 1024
    }
}
