package net.corda.httprpc.server.config.models

import java.nio.file.Path

data class HttpRpcSSLSettings(
    val keyStorePath: Path,
    val keyStorePassword: String
) {
    override fun toString() = "HttpRpcSSLSettings(keyStorePath=$keyStorePath)"
}