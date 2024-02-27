package net.corda.rest.ssl.impl

import net.corda.libs.configuration.SmartConfig
import net.corda.rest.ssl.KeyStoreInfo
import net.corda.rest.ssl.SslCertReadService
import net.corda.schema.configuration.BootConfig
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_CA_CRT_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_CRT_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_FILE_PATH
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEYSTORE_PASSWORD
import net.corda.schema.configuration.BootConfig.BOOT_REST_TLS_KEY_PATH
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.x509.CertificateFactory
import org.bouncycastle.openssl.PEMKeyPair
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

class SslCertReadServiceImpl(private val createDirectory: () -> Path) : SslCertReadService {

    constructor() : this(createDirectory = { Files.createTempDirectory("rest-ssl") })

    internal companion object {

        const val PASSWORD = "httpsPassword"

        const val KEYSTORE_NAME = "https.keystore"

        const val TLS_ENTRY = "tls_entry"

        const val KEYSTORE_TYPE = "pkcs12"

        private val log: Logger = LoggerFactory.getLogger(this::class.java.enclosingClass)

        private val String.withoutPrefix: String
            get() {
                return this.removePrefix("${BootConfig.BOOT_REST}.")
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
        } else if (config.hasPath(BOOT_REST_TLS_CRT_PATH)) {
            val tlsCertPath = config.getString(BOOT_REST_TLS_CRT_PATH)
            val tlsKeyPath = config.getString(BOOT_REST_TLS_KEY_PATH)
            val caChainPath = config.getString(BOOT_REST_TLS_CA_CRT_PATH)
            createKeyStoreInfoFromCrt(tlsCertPath, tlsKeyPath, caChainPath)
        } else {
            log.warn(
                "Using default self-signed TLS certificate for REST endpoint. To stop seeing this message, please use bootstrap " +
                    "parameters: " +
                    "('-r${BOOT_REST_TLS_KEYSTORE_FILE_PATH.withoutPrefix}' and " +
                    "'-r${BOOT_REST_TLS_KEYSTORE_PASSWORD.withoutPrefix}')" +
                    " or " +
                    "('-r${BOOT_REST_TLS_CRT_PATH.withoutPrefix}', '-r${BOOT_REST_TLS_KEY_PATH.withoutPrefix}' " +
                    "and '-r${BOOT_REST_TLS_CA_CRT_PATH.withoutPrefix}')."
            )
            val tempDirectoryPath = createDirectory()
            val keyStorePath = Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME)
            keyStorePath.toFile().writeBytes(loadKeystoreFromResources())
            KeyStoreInfo(keyStorePath, PASSWORD)
        }
        keyStoreInfo = localKeyStoreInfo
        return localKeyStoreInfo
    }

    private fun createKeyStoreInfoFromCrt(tlsCertPath: String, tlsKeyPath: String, caChainPath: String): KeyStoreInfo {
        val ks = KeyStore.getInstance(KEYSTORE_TYPE)
        ks.load(null, PASSWORD.toCharArray())

        val privateKey = readPrivateKey(tlsKeyPath)
        val certChain = readCertChain(tlsCertPath, caChainPath)

        ks.setKeyEntry(TLS_ENTRY, privateKey, PASSWORD.toCharArray(), certChain)

        val tempDirectoryPath = createDirectory()
        val keyStorePath = tempDirectoryPath.resolve(KEYSTORE_NAME)
        keyStorePath.outputStream().use {
            ks.store(it, PASSWORD.toCharArray())
        }

        return KeyStoreInfo(keyStorePath, PASSWORD)
    }

    private fun readCertChain(tlsKeyPath: String, caChainPath: String): Array<Certificate> {
        val certificateFactory = CertificateFactory()
        val leafCertificate = Path.of(tlsKeyPath).inputStream().use {
            certificateFactory.engineGenerateCertificate(it)
        }

        @Suppress("UNCHECKED_CAST")
        val certsChain = Path.of(caChainPath).inputStream().use {
            certificateFactory.engineGenerateCertificates(it)
        } as Collection<Certificate>

        return (listOf(leafCertificate) + certsChain).toTypedArray()
    }

    private fun readPrivateKey(tlsKeyPath: String): PrivateKey {
        val privateKeyInfo = File(tlsKeyPath).reader().use { keyReader ->
            val pemParser = PEMParser(keyReader)
            val pemObject = pemParser.readObject()
            if (pemObject is PEMKeyPair) {
                pemObject.privateKeyInfo
            } else {
                PrivateKeyInfo.getInstance(pemObject)
            }
        }
        return JcaPEMKeyConverter().getPrivateKey(privateKeyInfo)
    }

    private fun loadKeystoreFromResources(): ByteArray {
        val inputStream = this::class.java.classLoader.getResourceAsStream(KEYSTORE_NAME)
            ?: throw CordaRuntimeException("$KEYSTORE_NAME cannot be loaded")
        return inputStream.readAllBytes()
    }
}
