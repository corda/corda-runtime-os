package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.security.PublicKey
import java.security.cert.Certificate

class DelegatedKeystoreTest {
    private val signer = mock<DelegatedSigner>()
    private val certificatesStoreOne = mock<DelegatedCertificatesStore> {
        on { name } doReturn "one"
    }
    private val certificatesStoreTwo = mock<DelegatedCertificatesStore> {
        on { name } doReturn "two"
    }
    private val delegatedKeystore = DelegatedKeystore(
        listOf(
            certificatesStoreOne,
            certificatesStoreTwo
        ),
        signer
    )

    @Test
    fun `engineGetKey for unknown alias returns null`() {
        assertThat(delegatedKeystore.engineGetKey("three", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no certificates returns null`() {
        whenever(certificatesStoreTwo.certificates).doReturn(emptyList())

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no public key returns null`() {
        val certificate = mock<Certificate>()
        whenever(certificatesStoreTwo.certificates).doReturn(listOf(certificate))

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey will return DelegatedPrivateKey`() {
        val key = mock<PublicKey> {
            on { format } doReturn "format"
            on { algorithm } doReturn "algorithm"
        }
        val certificate = mock<Certificate> {
            on { publicKey } doReturn key
        }
        whenever(certificatesStoreOne.certificates).doReturn(listOf(certificate))

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isInstanceOf(DelegatedPrivateKey::class.java)
    }

    @Test
    fun `engineGetCertificate will return null for unknown alias`() {
        assertThat(delegatedKeystore.engineGetCertificate("three")).isNull()
    }

    @Test
    fun `engineGetCertificate will return the first certificate for known alias`() {
        val certificate = mock<Certificate>()
        whenever(certificatesStoreOne.certificates).doReturn(listOf(certificate))

        assertThat(delegatedKeystore.engineGetCertificate("one")).isSameAs(certificate)
    }

    @Test
    fun `engineGetCertificateChain will return null for unknown alias`() {
        assertThat(delegatedKeystore.engineGetCertificateChain("three")).isNull()
    }

    @Test
    fun `engineGetCertificateChain will return the certificates for known alias`() {
        val certificate1 = mock<Certificate>()
        val certificate2 = mock<Certificate>()
        whenever(certificatesStoreTwo.certificates).doReturn(listOf(certificate1, certificate2))

        assertThat(delegatedKeystore.engineGetCertificateChain("two")).contains(certificate1, certificate2)
    }

    @Test
    fun `engineAliases will return the list of aliases`() {
        assertThat(delegatedKeystore.engineAliases().toList()).containsExactly("one", "two")
    }

    @Test
    fun `engineContainsAlias will return true for know alias`() {
        assertThat(delegatedKeystore.engineContainsAlias("one")).isTrue
    }

    @Test
    fun `engineContainsAlias will return false for unknow alias`() {
        assertThat(delegatedKeystore.engineContainsAlias("three")).isFalse
    }

    @Test
    fun `engineSize will return the correct size`() {
        assertThat(delegatedKeystore.engineSize()).isEqualTo(2)
    }

    @Test
    fun `engineIsKeyEntry will return true for know alias`() {
        assertThat(delegatedKeystore.engineIsKeyEntry("one")).isTrue
    }

    @Test
    fun `engineLoad will not throw an exception`() {
        delegatedKeystore.engineLoad(null)
    }

    @Test
    fun `engineLoad with password will not throw an exception`() {
        delegatedKeystore.engineLoad(mock(), "".toCharArray())
    }

    @Nested
    inner class UnsupportedOperationsTest {
        @Test
        fun `engineSetKeyEntry with password`() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineSetKeyEntry(null, null, null, null)
            }
        }

        @Test
        fun `engineSetKeyEntry without password`() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineSetKeyEntry(null, null, null)
            }
        }

        @Test
        fun engineSetCertificateEntry() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineSetCertificateEntry(null, null)
            }
        }

        @Test
        fun engineDeleteEntry() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineDeleteEntry(null)
            }
        }

        @Test
        fun engineIsCertificateEntry() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineIsCertificateEntry(null)
            }
        }

        @Test
        fun engineGetCertificateAlias() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineGetCertificateAlias(null)
            }
        }

        @Test
        fun engineStore() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineStore(null, null)
            }
        }

        @Test
        fun engineGetCreationDate() {
            assertThrows<UnsupportedOperationException> {
                delegatedKeystore.engineGetCreationDate("")
            }
        }
    }
}
