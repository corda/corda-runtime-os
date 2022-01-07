package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.p2p.gateway.security.delegates.JksSigningService
import net.corda.p2p.gateway.security.delegates.SecurityDelegateProvider
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

    val rawTrustStore: ByteArray,

    /**
     * The trust store password
     */
    val trustStorePassword: String,

    /**
     * Property determining how the revocation check will be made for the server certificate
     */
    val revocationCheck: RevocationConfig,
) {
    /**
     * The key store used for TLS connections
     */
    val keyStore: KeyStore by lazy {
        SecurityDelegateProvider.createKeyStore(JksSigningService(rawKeyStore, keyStorePassword))
    }
    /**
     * The trust root key store used to validate the peer certificate
     */
    val trustStore: KeyStore by lazy {
        readKeyStore(rawTrustStore, trustStorePassword)
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
        if (!rawTrustStore.contentEquals(other.rawTrustStore)) return false
        if (trustStorePassword != other.trustStorePassword) return false
        if (revocationCheck != other.revocationCheck) return false

        return true
    }

    override fun hashCode(): Int {
        var result = rawKeyStore.contentHashCode()
        result = 31 * result + keyStorePassword.hashCode()
        result = 31 * result + rawTrustStore.contentHashCode()
        result = 31 * result + trustStorePassword.hashCode()
        result = 31 * result + revocationCheck.hashCode()
        return result
    }
}

internal fun Config.toSslConfiguration(): SslConfiguration {
    val revocationCheckMode = this.getEnum(RevocationConfigMode::class.java, "revocationCheck.mode")
    return SslConfiguration(
        rawKeyStore = this.getString("keyStore").base64ToByteArray(),
        keyStorePassword = this.getString("keyStorePassword"),
        rawTrustStore = this.getString("trustStore").base64ToByteArray(),
        trustStorePassword = this.getString("trustStorePassword"),
        revocationCheck = RevocationConfig(revocationCheckMode)
    )
}
