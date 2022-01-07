package net.corda.p2p.gateway.security.delegates

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.security.cert.Certificate

class DelegateKeyStoreTest {
    private val onePublicKey = mock<PublicKey> {
        on { format } doReturn "format"
        on { algorithm } doReturn "algorithm"
    }
    private val certificateOne = mock<Certificate> {
        on { publicKey } doReturn onePublicKey
    }
    private val certificateTwoA = mock<Certificate> {
        on { publicKey } doReturn mock()
    }
    private val certificateTwoB = mock<Certificate> {
        on { publicKey } doReturn mock()
    }
    private val aliasOne = mock<SigningService.Alias> {
        on { name } doReturn "one"
        on { certificates } doReturn listOf(certificateOne)
    }
    private val aliasTwo = mock<SigningService.Alias> {
        on { name } doReturn "two"
        on { certificates } doReturn listOf(certificateTwoA, certificateTwoB)
    }
    private val aliasThree = mock<SigningService.Alias> {
        on { name } doReturn "three"
        on { certificates } doReturn emptyList()
    }
    private val service = mock<SigningService> {
        on { aliases } doReturn listOf(aliasTwo, aliasOne, aliasThree)
    }

    @Test
    fun `engineLoad ignores unknown types`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(mock())

        assertThat(keyStore.engineGetCertificateChain("one")).isEmpty()
    }

    @Test
    fun `engineGetCertificateChain return empty list when there is no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineGetCertificateChain("one")).isEmpty()
    }

    @Test
    fun `engineGetCertificateChain return correct list of aliases`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetCertificateChain("one")).contains(certificateOne)
    }

    @Test
    fun `engineGetCertificateChain return empty list for unknown alias`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetCertificateChain("four")).isEmpty()
    }

    @Test
    fun `engineGetCertificate return empty list when there is no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineGetCertificate("one")).isNull()
    }

    @Test
    fun `engineGetCertificate return first item when there are certificates`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetCertificate("two")).isEqualTo(certificateTwoA)
    }

    @Test
    fun `engineGetCertificate return null when there are no certificates`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetCertificate("three")).isNull()
    }

    @Test
    fun `engineAliases return empty collection when there is no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineAliases().toList()).isEmpty()
    }

    @Test
    fun `engineAliases return the list of aliases`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineAliases().toList()).contains("one", "two", "three")
    }

    @Test
    fun `engineContainsAlias return false if keystore has no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineContainsAlias("one")).isFalse
    }

    @Test
    fun `engineContainsAlias return true if keystore has alias`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineContainsAlias("one")).isTrue
    }

    @Test
    fun `engineIsKeyEntry return false if keystore has no alias`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineIsKeyEntry("false")).isFalse
    }

    @Test
    fun `engineSize return 0 if key store has no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineSize()).isZero
    }

    @Test
    fun `engineSize return real size if key store has service`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineSize()).isEqualTo(3)
    }

    @Test
    fun `engineGetKey return null if key store has no service`() {
        val keyStore = DelegateKeyStore()

        assertThat(keyStore.engineGetKey("one", "one".toCharArray())).isNull()
    }

    @Test
    fun `engineGetKey return null for unknown alias`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetKey("four", "one".toCharArray())).isNull()
    }

    @Test
    fun `engineGetKey return null for alias without certificates`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        assertThat(keyStore.engineGetKey("three", "one".toCharArray())).isNull()
    }

    @Test
    fun `engineGetKey return correct key for alias with certificates`() {
        val keyStore = DelegateKeyStore()
        keyStore.engineLoad(DelegateKeyStore.LoadParameter(service))

        val key = keyStore.engineGetKey("one", "one".toCharArray())

        assertThat(key).isInstanceOf(DelegatedPrivateKey::class.java)
    }

    @Nested
    inner class UnsupportedOperationTests {
        private val keyStore = DelegateKeyStore()
        @Test
        fun engineIsCertificateEntry() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineIsCertificateEntry(null)
            }
        }

        @Test
        fun engineGetCertificateAlias() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineGetCertificateAlias(null)
            }
        }

        @Test
        fun engineGetCreationDate() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineGetCreationDate(null)
            }
        }

        @Test
        fun `engineSetKeyEntry with password`() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineSetKeyEntry(null, null, null, null)
            }
        }
        @Test
        fun `engineSetKeyEntry without password`() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineSetKeyEntry(null, null, null)
            }
        }

        @Test
        fun engineSetCertificateEntry() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineSetCertificateEntry(null, null)
            }
        }

        @Test
        fun engineDeleteEntry() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineDeleteEntry(null)
            }
        }

        @Test
        fun engineStore() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineStore(null, null)
            }
        }

        @Test
        fun engineLoad() {
            assertThrows<UnsupportedOperationException> {
                keyStore.engineLoad(null, null)
            }
        }
    }
}
