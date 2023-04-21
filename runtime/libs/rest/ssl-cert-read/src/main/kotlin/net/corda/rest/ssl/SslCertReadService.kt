package net.corda.rest.ssl

import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle
import java.nio.file.Path

interface SslCertReadService : Lifecycle {

    fun getOrCreateKeyStoreInfo(config: SmartConfig): KeyStoreInfo
}

data class KeyStoreInfo(val path: Path, val password: String)