package net.corda.crypto.delegated.signing

import net.corda.crypto.delegated.signing.DelegatedSignerInstaller.Companion.RSA_SIGNING_ALGORITHM
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.security.PublicKey
import java.security.Security
import java.security.cert.Certificate

class DelegatedKeystoreProviderTest {
    @Test
    fun `newInstance return a DelegatedKeystore`() {
        val delegateSigner = mock<DelegatedSigner>()
        val delegateCertificates = mock<DelegatedCertificateStore>()
        val provider = DelegatedKeystoreProvider()
        provider.putServices("service", delegateSigner, delegateCertificates)
        val service = provider.getService("KeyStore", "service")

        val delegate = service.newInstance(null)

        assertThat(delegate).isInstanceOf(DelegatedKeystore::class.java)
    }

    @Test
    fun `newInstance return a DelegatedSignature`() {
        val delegateSigner = mock<DelegatedSigner>()
        val delegateCertificates = mock<DelegatedCertificateStore>()
        val provider = DelegatedKeystoreProvider()
        provider.putServices("service", delegateSigner, delegateCertificates)
        val service = provider.getService("Signature", RSA_SIGNING_ALGORITHM)

        val delegate = service.newInstance(null)

        assertThat(delegate).isInstanceOf(DelegatedSignature::class.java)
    }

    @Test
    fun `putService will link the signer with the certificates`() {
        val delegateSignerOne = mock<DelegatedSigner>()
        val delegateSignerTwo = mock<DelegatedSigner>()
        val publicKeyOne = mock<PublicKey>()
        val delegateCertificatesOne = object : DelegatedCertificateStore {
            override val aliasToCertificates = (1..3).associate {
                val certificate = mock<Certificate> {
                    on { publicKey } doReturn publicKeyOne
                }

                "One.$it" to listOf(certificate)
            }
        }
        val publicKeyTwo = mock<PublicKey>()
        val delegateCertificatesTwo = object : DelegatedCertificateStore {
            override val aliasToCertificates = (1..3).associate {
                val certificate = mock<Certificate> {
                    on { publicKey } doReturn publicKeyTwo
                }

                "Two.$it" to listOf(certificate)
            }
        }

        val provider = DelegatedKeystoreProvider()

        provider.putServices("serviceOne", delegateSignerOne, delegateCertificatesOne)
        provider.putServices("serviceTwo", delegateSignerTwo, delegateCertificatesTwo)

        val serviceOne = provider.getService("KeyStore", "serviceOne")
        val serviceTwo = provider.getService("KeyStore", "serviceTwo")
        val delegateOne = serviceOne.newInstance(null) as? DelegatedKeystore
        val delegateTwo = serviceTwo.newInstance(null) as? DelegatedKeystore
        val keyOne = delegateOne?.engineGetKey("One.1", "password".toCharArray()) as? DelegatedPrivateKey
        val keyTwo = delegateTwo?.engineGetKey("Two.3", "password".toCharArray()) as? DelegatedPrivateKey

        assertThat(keyOne?.signer).isSameAs(delegateSignerOne)
        assertThat(keyTwo?.signer).isSameAs(delegateSignerTwo)
    }

    @Nested
    inner class ProviderTests {
        @AfterEach
        fun cleanUp() {
            Security.removeProvider("DelegatedKeyStore")
        }

        @Test
        fun `provider return provider if already exists`() {
            val currentProvider = mock<DelegatedKeystoreProvider> {
                on { name } doReturn "DelegatedKeyStore"
            }
            Security.addProvider(currentProvider)

            assertThat(DelegatedKeystoreProvider.provider).isSameAs(currentProvider)
        }

        @Test
        fun `provider return new provider if not first`() {
            assertThat(DelegatedKeystoreProvider.provider).isInstanceOf(DelegatedKeystoreProvider::class.java)
        }

        @Test
        fun `provider adds provider to the security`() {
            DelegatedKeystoreProvider.provider

            assertThat(Security.getProviders()[0]).isEqualTo(DelegatedKeystoreProvider.provider)
        }
    }
}
