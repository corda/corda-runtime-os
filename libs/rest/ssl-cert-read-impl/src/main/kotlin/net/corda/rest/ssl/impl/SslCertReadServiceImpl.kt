package net.corda.rest.ssl.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.rest.ssl.KeyStoreInfo
import net.corda.rest.ssl.SslCertReadService
import net.corda.utilities.VisibleForTesting
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceImpl(private val createDirectory: () -> Path) : SslCertReadService {

    constructor() : this(createDirectory = { Files.createTempDirectory("rest-ssl") })

    internal companion object {
        @VisibleForTesting
        const val PASSWORD = "httpsPassword"

        @VisibleForTesting
        const val KEYSTORE_NAME = "https.keystore"
    }

    @Volatile
    private var keyStoreInfo: KeyStoreInfo? = null

    override val isRunning: Boolean
        get() = _isRunning

    @Volatile
    private var _isRunning = true

    override fun start() {
    }

    override fun stop() {
        if (isRunning) {
            synchronized(this) {
                if (isRunning) {
                    keyStoreInfo?.run { File(path.toFile().parent).deleteRecursively() }
                    _isRunning = false
                    keyStoreInfo = null
                }
            }
        }
    }

    override fun getOrCreateKeyStoreInfo(config: SmartConfig): KeyStoreInfo {
        if (keyStoreInfo == null) {
            val tempDirectoryPath = createDirectory()
            val keyStorePath = Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME)
            keyStorePath.toFile().writeBytes(loadKeystoreFromResources())
            keyStoreInfo = KeyStoreInfo(keyStorePath, PASSWORD)
        }
        return keyStoreInfo!!
    }

    private fun loadKeystoreFromResources(): ByteArray {
        val inputStream = this::class.java.classLoader.getResourceAsStream(KEYSTORE_NAME)
            ?: throw CordaRuntimeException("$KEYSTORE_NAME cannot be loaded")
        return inputStream.readAllBytes()
    }
}