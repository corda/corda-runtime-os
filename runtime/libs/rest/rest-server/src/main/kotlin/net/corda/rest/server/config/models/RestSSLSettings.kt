package net.corda.rest.server.config.models

import java.nio.file.Path

data class RestSSLSettings(
    val keyStorePath: Path,
    val keyStorePassword: String
) {
    override fun toString() = "RestSSLSettings(keyStorePath=$keyStorePath)"
}