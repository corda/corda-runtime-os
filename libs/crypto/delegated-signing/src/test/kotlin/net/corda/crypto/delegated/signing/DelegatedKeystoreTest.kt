package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import java.security.Provider
import java.security.PublicKey
import java.security.Security
import java.security.cert.Certificate

class DelegatedKeystoreTest {
    private val aliasOne = mock<DelegatedSigningService.Alias> {
        on { name } doReturn "one"
    }
    private val aliasTwo = mock<DelegatedSigningService.Alias> {
        on { name } doReturn "two"
    }
    private val signingService = mock<DelegatedSigningService> {
        on { aliases } doReturn listOf(aliasOne, aliasTwo)
    }
    private val delegatedKeystore = DelegatedKeystore(signingService)

    @Test
    fun `engineGetKey for unknown alias returns null`() {
        assertThat(delegatedKeystore.engineGetKey("three", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no certificates returns null`() {
        whenever(aliasOne.certificates).doReturn(emptyList())

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isNull()
    }

    @Test
    fun `engineGetKey for alias with no public key returns null`() {
        val certificate = mock<Certificate>()
        whenever(aliasOne.certificates).doReturn(listOf(certificate))

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
        whenever(aliasOne.certificates).doReturn(listOf(certificate))

        assertThat(delegatedKeystore.engineGetKey("one", CharArray(0))).isInstanceOf(DelegatedPrivateKey::class.java)
    }

    @Test
    fun `engineGetCertificate will return null for unknown alias`() {
        assertThat(delegatedKeystore.engineGetCertificate("three")).isNull()
    }

    @Test
    fun `engineGetCertificate will return the first certificate for known alias`() {
        val certificate = mock<Certificate>()
        whenever(aliasOne.certificates).doReturn(listOf(certificate))

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
        whenever(aliasOne.certificates).doReturn(listOf(certificate1, certificate2))

        assertThat(delegatedKeystore.engineGetCertificateChain("one")).contains(certificate1, certificate2)
    }

    @Test
    fun `engineAliases will return the list of aliases`() {
        assertThat(delegatedKeystore.engineAliases()?.toList()).containsExactly("one", "two")
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
    fun `engineLoad will insert Delegated signature provider if there are no provider`() {
        mockStatic(Security::class.java).use { mockSecurity ->
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(emptyArray())

            delegatedKeystore.engineLoad(null)

            mockSecurity.verify {
                Security.insertProviderAt(any<DelegatedSignatureProvider>(), eq(1))
            }
        }
    }

    @Test
    fun `engineLoad will not insert Delegated signature provider if already added`() {
        mockStatic(Security::class.java).use { mockSecurity ->
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(arrayOf(mock(), mock<DelegatedSignatureProvider>()))

            delegatedKeystore.engineLoad(null)

            mockSecurity.verify({
                Security.insertProviderAt(any<DelegatedSignatureProvider>(), eq(1))
            }, never())
        }
    }

    @Test
    fun `engineLoad will insert Delegated signature provider if was not added before`() {
        mockStatic(Security::class.java).use { mockSecurity ->
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(arrayOf(mock(), mock(), mock()))

            delegatedKeystore.engineLoad(mock(), CharArray(0))

            mockSecurity.verify {
                Security.insertProviderAt(any<DelegatedSignatureProvider>(), eq(1))
            }
        }
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
