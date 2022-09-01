package net.corda.httprpc.server.config.models

import net.corda.base.util.NetworkHostAndPort

data class HttpRpcSettings(
    val address: NetworkHostAndPort,
    val context: HttpRpcContext,
    val ssl: HttpRpcSSLSettings?,
    val sso: SsoSettings? = null,
    /**
     * The maximum content length in bytes accepted for POST requests
     */
    val maxContentLength: Int
) {
    companion object {
        const val MAX_CONTENT_LENGTH_DEFAULT_VALUE = 1024 * 1024
    }
}
