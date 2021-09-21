package net.corda.httprpc.ssl.impl

import net.corda.httprpc.ssl.KeyStoreInfo
import net.corda.httprpc.ssl.SslCertReadService
import net.corda.v5.base.annotations.VisibleForTesting
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceStubImpl(private val createDirectory: () -> Path) : SslCertReadService {

    constructor() : this(createDirectory = { Files.createTempDirectory("http-rpc-ssl") })

    internal companion object {
        @VisibleForTesting
        const val PASSWORD = "httpsPassword"

        @VisibleForTesting
        const val KEYSTORE_NAME = "https.keystore"
    }

    private var keyStoreInfo: KeyStoreInfo? = null

    override val isRunning: Boolean
        get() = _isRunning

    private var _isRunning = true

    override fun start() {
        // Stub implementation so ignore starting the service
    }

    override fun stop() {
        if (isRunning) {
            synchronized(isRunning) {
                if (isRunning) {
                    keyStoreInfo?.run { File(path.toFile().parent).deleteRecursively() }
                    _isRunning = false
                }
            }
        }
    }

    override fun getOrCreateKeyStore(): KeyStoreInfo {
        if (keyStoreInfo == null) {
            val tempDirectoryPath = createDirectory()
            val keyStorePath = Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME)
            keyStorePath.toFile().writeBytes(loadKeystoreFromResources())
            keyStoreInfo = KeyStoreInfo(keyStorePath, PASSWORD)
        }
        return keyStoreInfo!!
    }

    private fun loadKeystoreFromResources(): ByteArray {
        return File(this::class.java.classLoader.getResource(KEYSTORE_NAME)!!.toURI()).inputStream().readAllBytes()
    }
}