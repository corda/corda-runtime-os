package net.corda.httprpc.ssl

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory

class SslCertReadServiceStubImpl(private val createDirectory: () -> Path) : SslCertReadService {

    constructor() : this(createDirectory = { Files.createTempDirectory("http-rpc-ssl") })

    private companion object {
        const val PASSWORD = "Corda"
        const val KEYSTORE_NAME = "ssl-keystore.jks"
        const val CERT_TYPE = "X.509"
        const val CERT_NAME = "cert.pem"

        val PASSWORD_CHAR_ARRAY = PASSWORD.toCharArray()
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
            KeyStore.getInstance(KeyStore.getDefaultType()).run {
                // Create new keystore (by specifying null)
                load(null, PASSWORD_CHAR_ARRAY)
                // Save the certificate from the resources dir to the keystore
                setCertificateEntry("stub", loadCertificateFromResource())
                store(tempDirectoryPath)
            }
            keyStoreInfo = KeyStoreInfo(Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME), PASSWORD)
        }
        return keyStoreInfo!!
    }

    private fun KeyStore.store(tempDirectoryPath: Path) {
        val keyStorePath = Path.of(tempDirectoryPath.toString(), KEYSTORE_NAME).toString()
        require(!File(keyStorePath).exists()) { "The keystore already exists and should not be recreated" }
        FileOutputStream(keyStorePath).use { outputStream ->
            store(outputStream, PASSWORD_CHAR_ARRAY)
        }
    }

    private fun loadCertificateFromResource(): Certificate {
        val certificateFactory = CertificateFactory.getInstance(CERT_TYPE)
        return File(this::class.java.classLoader.getResource(CERT_NAME)!!.toURI()).inputStream().use { inputStream ->
            certificateFactory.generateCertificate(inputStream)
        }
    }
}