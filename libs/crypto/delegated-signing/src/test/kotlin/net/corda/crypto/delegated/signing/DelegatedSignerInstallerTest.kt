package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.Provider
import java.security.Security

class DelegatedSignerInstallerTest {
    private val mockSecurity = mockStatic(Security::class.java)
    private val installer = DelegatedSignerInstaller()

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
    }

    @Nested
    inner class FindOriginalSignatureProviderTest {
        private val myProvider = mock<DelegatedKeystoreProvider>()
        private val providerWithNoServices = mock<Provider> {
            on { services } doReturn emptySet<Provider.Service>()
        }
        private val serviceWithWrongAlgorithms = mock<Provider.Service> {
            on { algorithm } doReturn "nop"
        }
        private val providerWithBadAlgorithmService = mock<Provider> {
            on { services } doReturn setOf(serviceWithWrongAlgorithms)
        }
        private val serviceWithWrongType = mock<Provider.Service> {
            on { algorithm } doReturn "alg"
            on { type } doReturn "nop"
        }
        private val providerWithBadTypeService = mock<Provider> {
            on { services } doReturn setOf(serviceWithWrongType)
        }
        private val goodService = mock<Provider.Service> {
            on { algorithm } doReturn "alg"
            on { type } doReturn "Signature"
        }
        private val providerWithExpectedService = mock<Provider> {
            on { services } doReturn setOf(goodService)
        }

        @Test
        fun `findOriginalSignatureProvider returns the correct provider`() {
            whenever(goodService.provider).doReturn(providerWithExpectedService)
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    myProvider,
                    providerWithNoServices,
                    providerWithBadAlgorithmService,
                    providerWithBadTypeService,
                    providerWithExpectedService
                )
            )

            val provider = installer.findOriginalSignatureProvider("alg")

            assertThat(provider).isSameAs(providerWithExpectedService)
        }

        @Test
        fun `findOriginalSignatureProvider throws exception when the service is not pointing to a provider`() {
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    myProvider,
                    providerWithNoServices,
                    providerWithBadAlgorithmService,
                    providerWithBadTypeService,
                    providerWithExpectedService
                )
            )

            assertThrows<SecurityException> {
                installer.findOriginalSignatureProvider("alg")
            }
        }

        @Test
        fun `findOriginalSignatureProvider throws exception when it can't find a provider`() {
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    myProvider,
                    providerWithNoServices,
                    providerWithBadAlgorithmService,
                    providerWithBadTypeService,
                )
            )

            assertThrows<SecurityException> {
                installer.findOriginalSignatureProvider("alg")
            }
        }
    }

    @Test
    fun `install put the service in the provider`() {
        val provider = mock<DelegatedKeystoreProvider>()
        mockSecurity.`when`<Provider> {
            Security.getProvider("DelegatedKeyStore")
        }.doReturn(
            provider
        )
        val signer = mock<DelegatedSigner>()
        val certificates = listOf(mock(), mock<DelegatedCertificatesStore>())

        installer.install("name", signer, certificates)

        verify(provider).putServices("name", signer, certificates)
    }
}
