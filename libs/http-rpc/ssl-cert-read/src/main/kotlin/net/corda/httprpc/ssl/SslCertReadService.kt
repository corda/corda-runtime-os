package net.corda.httprpc.ssl

import net.corda.lifecycle.Lifecycle
import java.nio.file.Path

interface SslCertReadService : Lifecycle {

    fun getOrCreateKeyStore(): KeyStoreInfo
}

data class KeyStoreInfo(val path: Path, val password: String)