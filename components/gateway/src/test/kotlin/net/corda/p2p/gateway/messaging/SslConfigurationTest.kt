package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.InputStream
import java.security.KeyStore

class SslConfigurationTest {

    /*
    val keyStore: KeyStore by lazy {
        readKeyStore(rawKeyStore, keyStorePassword)
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
    
     */
    @Test
    fun `toSslConfiguration return correct configuration`() {
        val config = mock<Config> {
            on { getEnum(RevocationConfigMode::class.java, "revocationCheck.mode") } doReturn RevocationConfigMode.SOFT_FAIL
            on { getString("keyStore") } doReturn byteArrayOf(7, 8).toBase64()
            on { getString("keyStorePassword") } doReturn "passA"
            on { getString("trustStore") } doReturn byteArrayOf(1, 2).toBase64()
            on { getString("trustStorePassword") } doReturn "passB"
        }

        val sslConfiguration = config.toSslConfiguration()

        assertThat(sslConfiguration).isEqualTo(
            SslConfiguration(
                rawTrustStore = byteArrayOf(1, 2),
                rawKeyStore = byteArrayOf(7, 8),
                keyStorePassword = "passA",
                trustStorePassword = "passB",
                revocationCheck =
                RevocationConfig(RevocationConfigMode.SOFT_FAIL)
            )
        )
    }

    @Test
    fun `keyStore create key store`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val keyStore = mock<KeyStore>()
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            assertThat(config.keyStore).isSameAs(keyStore)
        }
    }

    @Test
    fun `keyStore load correct data`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val data = argumentCaptor<InputStream>()
        val password = argumentCaptor<CharArray>()
        val keyStore = mock<KeyStore> {
            on { load(data.capture(), password.capture()) } doAnswer {}
        }
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            config.keyStore
        }

        assertThat(data.firstValue.readAllBytes()).isEqualTo(byteArrayOf(1, 2, 3, 4))
    }

    @Test
    fun `keyStore load correct password`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val data = argumentCaptor<InputStream>()
        val password = argumentCaptor<CharArray>()
        val keyStore = mock<KeyStore> {
            on { load(data.capture(), password.capture()) } doAnswer {}
        }
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            config.keyStore
        }

        assertThat(password.firstValue).isEqualTo("password".toCharArray())
    }
    @Test
    fun `trustStore create key store`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val keyStore = mock<KeyStore>()
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            assertThat(config.trustStore).isSameAs(keyStore)
        }
    }

    @Test
    fun `trustStore load correct data`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val data = argumentCaptor<InputStream>()
        val password = argumentCaptor<CharArray>()
        val keyStore = mock<KeyStore> {
            on { load(data.capture(), password.capture()) } doAnswer {}
        }
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            config.trustStore
        }

        assertThat(data.firstValue.readAllBytes()).isEqualTo(byteArrayOf(1, 2))
    }

    @Test
    fun `trustStore load correct password`() {
        val config = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val data = argumentCaptor<InputStream>()
        val password = argumentCaptor<CharArray>()
        val keyStore = mock<KeyStore> {
            on { load(data.capture(), password.capture()) } doAnswer {}
        }
        mockStatic(KeyStore::class.java).use { mockStatic ->
            mockStatic.`when`<KeyStore> {
                KeyStore.getInstance("JKS")
            }.doReturn(keyStore)

            config.trustStore
        }

        assertThat(password.firstValue).isEqualTo("passB".toCharArray())
    }

    @Test
    fun `hashCode is different if content is different`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2, 3),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )

        assertThat(config1.hashCode()).isNotEqualTo(config2.hashCode())
    }

    @Test
    fun `equals return true for the same values`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )

        assertThat(config1).isEqualTo(config1)
    }

    @Test
    fun `equals return false for another object`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )

        assertThat(config1).isNotEqualTo("config1")
    }

    @Test
    fun `equals return false for another keyStore`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy(rawKeyStore = byteArrayOf(1, 2, 3))

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `equals return false for another keyStorePassword`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy(keyStorePassword = "--")

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `equals return false for another rawTrustStore`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy(rawTrustStore = byteArrayOf(3, 3, 3))

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `equals return false for another trustStorePassword`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy(trustStorePassword = "passc")

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `equals return false for another revocationCheck`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy(revocationCheck = RevocationConfig(RevocationConfigMode.OFF))

        assertThat(config1).isNotEqualTo(config2)
    }

    @Test
    fun `equals return true if data is the same`() {
        val config1 = SslConfiguration(
            rawKeyStore = byteArrayOf(1, 2, 3, 4),
            keyStorePassword = "password",
            rawTrustStore = byteArrayOf(1, 2),
            trustStorePassword = "passB",
            revocationCheck =
            RevocationConfig(RevocationConfigMode.SOFT_FAIL)
        )
        val config2 = config1.copy()

        assertThat(config1).isEqualTo(config2)
    }
}
