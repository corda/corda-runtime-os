package net.corda.rest.ssl.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.rest.ssl.KeyStoreInfo
import net.corda.rest.ssl.SslCertReadService
import net.corda.schema.configuration.BootConfig.BOOT_REST_PARAMS
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_PASSWORD
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class SslCertReadServiceImpl(private val createDirectory: () -> Path) : SslCertReadService {

    constructor() : this(createDirectory = { Files.createTempDirectory("rest-ssl") })

    internal companion object {
        const val PASSWORD = "httpsPassword"

        const val KEYSTORE_NAME = "https.keystore"

        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val String.withoutPrefix: String
            get() {
                return this.removePrefix("$BOOT_REST_PARAMS.")
            }
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
                    _isRunning = false
                    keyStoreInfo = null
                }
            }
        }
    }

    override fun getOrCreateKeyStoreInfo(config: SmartConfig): KeyStoreInfo {
        var localKeyStoreInfo = keyStoreInfo

        if (localKeyStoreInfo != null) return localKeyStoreInfo

        localKeyStoreInfo = if (config.hasPath(BOOT_REST_TLS_KEYSTORE_FILE_PATH)) {
            val bootKeyStorePath = config.getString(BOOT_REST_TLS_KEYSTORE_FILE_PATH)
            val keyStorePassword = config.getString(BOOT_REST_TLS_KEYSTORE_PASSWORD)
            KeyStoreInfo(Path.of(bootKeyStorePath), keyStorePassword)
        } else {
            log.warn(
                "Using default self-signed TLS certificate. To stop seeing this message, please use bootstrap " +
                        "parameters: '-r${BOOT_REST_TLS_KEYSTORE_FILE_PATH.withoutPrefix}' and " +
                        "'-r${BOOT_REST_TLS_KEYSTORE_PASSWORD.withoutPrefix}'."
            )
            val tempDirectoryPath = createDirectory()
            val keyStorePath = Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME)
            keyStorePath.toFile().writeBytes(loadKeystoreFromResources())
            KeyStoreInfo(keyStorePath, PASSWORD)
        }
        keyStoreInfo = localKeyStoreInfo
        return localKeyStoreInfo
    }

    private fun loadKeystoreFromResources(): ByteArray {
        val inputStream = this::class.java.classLoader.getResourceAsStream(KEYSTORE_NAME)
            ?: throw CordaRuntimeException("$KEYSTORE_NAME cannot be loaded")
        return inputStream.readAllBytes()
    }
}