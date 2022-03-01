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
    val firstCertificates = (1..3).map {
        mock<Certificate>()
    }
    private val certificatesStore = object : DelegatedCertificateStore {
        override val aliasToCertificates = mapOf(
            "one" to firstCertificates,
            "two" to emptyList()
        )
    }
    private val delegatedKeystore = DelegatedKeystore(
        certificatesStore,
        signer,
    )

    @Test
    fun `engineGetKey for unknown alias returns null`() {
        assertThat(delegatedKeystore.engineGetKey("three", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no certificates returns null`() {
        assertThat(delegatedKeystore.engineGetKey("two", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no public key returns null`() {
        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey will return DelegatedPrivateKey`() {
        val key = mock<PublicKey> {
            on { format } doReturn "format"
            on { algorithm } doReturn "algorithm"
        }
        whenever(firstCertificates[0].publicKey).doReturn(key)

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isInstanceOf(DelegatedPrivateKey::class.java)
    }

    @Test
    fun `engineGetCertificate will return null for unknown alias`() {
        assertThat(delegatedKeystore.engineGetCertificate("three")).isNull()
    }

    @Test
    fun `engineGetCertificate will return the first certificate for known alias`() {
        assertThat(delegatedKeystore.engineGetCertificate("one")).isSameAs(firstCertificates[0])
    }

    @Test
    fun `engineGetCertificateChain will return null for unknown alias`() {
        assertThat(delegatedKeystore.engineGetCertificateChain("three")).isNull()
    }

    @Test
    fun `engineGetCertificateChain will return the certificates for known alias`() {
        assertThat(delegatedKeystore.engineGetCertificateChain("one")).containsAll(firstCertificates)
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
    fun `engineContainsAlias will return false for unknown alias`() {
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
