package net.corda.p2p.gateway.messaging

import com.typesafe.config.Config
import net.corda.v5.base.util.toBase64
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockConstruction
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.security.KeyStore

class SslConfigurationTest {
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
        mockConstruction(JksKeyStoreReader::class.java).use {
            mockConstruction(KeyStoreFactory::class.java) { mock, _ ->
                whenever(mock.createDelegatedKeyStore()).doReturn(keyStore)
            }.use {
                assertThat(config.keyStore).isSameAs(keyStore)
            }
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
        mockConstruction(KeyStoreFactory::class.java).use {
            mockConstruction(JksKeyStoreReader::class.java) { _, context ->
                assertThat(context.arguments()[0]).isEqualTo(byteArrayOf(1, 2, 3, 4))
            }.use {
                config.keyStore
            }
        }
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
        mockConstruction(KeyStoreFactory::class.java).use {
            mockConstruction(JksKeyStoreReader::class.java) { _, context ->
                assertThat(context.arguments()[1]).isEqualTo("password")
            }.use {
                config.keyStore
            }
        }
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
        val config2 = config1.copy()

        assertThat(config1).isEqualTo(config2)
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
