package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.v5.base.util.base64ToByteArray
import java.io.ByteArrayInputStream
import java.security.KeyStore

/**
 * Simple configuration to be used for one-way TLS between Gateways. Since a gateway is both a client and a server,
 * it will require all properties to be configured. The [keyStore] and [keyStorePassword] respectively are needed by the
 * server to present a valid certificate during TLS handshake, whereas the [trustStore] and [trustStorePassword] are used
 * by the client to validate the received certificate.
 */
data class SslConfiguration(
    val rawKeyStore: ByteArray,

    /**
     * The password for the key store
     */
    val keyStorePassword: String,

    /**
     * Property determining how the revocation check will be made for the server certificate
     */
    val revocationCheck: RevocationConfig,
) {
    private val storeReader by lazy {
        JksKeyStoreReader(
            rawKeyStore,
            keyStorePassword
        )
    }
    /**
     * The key store used for TLS connections
     */
    val keyStore: KeyStore by lazy {
        KeyStoreFactory(
            storeReader.signer,
            storeReader.certificateStore,
        ).createDelegatedKeyStore()
    }

    private fun readKeyStore(rawData: ByteArray, password: String): KeyStore {
        return KeyStore.getInstance("JKS").also {
            ByteArrayInputStream(rawData).use { keySoreInputStream ->
                it.load(keySoreInputStream, password.toCharArray())
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SslConfiguration) return false

        if (!rawKeyStore.contentEquals(other.rawKeyStore)) return false
        if (keyStorePassword != other.keyStorePassword) return false
        if (revocationCheck != other.revocationCheck) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawKeyStore.contentHashCode()
        result = 31 * result + keyStorePassword.hashCode()
        result = 31 * result + revocationCheck.hashCode()
        return result
    }
}

internal fun Config.toSslConfiguration(): SslConfiguration {
    val revocationCheckMode = this.getEnum(RevocationConfigMode::class.java, "revocationCheck.mode")
    return SslConfiguration(
        rawKeyStore = this.getString("keyStore").base64ToByteArray(),
        keyStorePassword = this.getString("keyStorePassword"),
        revocationCheck = RevocationConfig(revocationCheckMode)
    )
}
