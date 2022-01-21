package net.corda.crypto.delegated.signing

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyStore
import java.security.Provider
import java.security.Security

class DelegatedSigningServiceTest {
    private val delegatedKeystoreProvider = mock<DelegatedKeystoreProvider>()
    private val mockSecurity = mockStatic(Security::class.java).also { mock ->
        mock.`when`<Provider> {
            Security.getProvider(DelegatedKeystoreProvider.PROVIDER_NAME)
        }.doReturn(delegatedKeystoreProvider)
    }

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
    }

    @Test
    fun `init adds service to provider`() {
        val service = object : DelegatedSigningService("name") {
            override val aliases = emptyList<Alias>()
        }

        verify(delegatedKeystoreProvider).putService("name", service)
    }

    @Test
    fun `init will not add provider if one exists`() {
        object : DelegatedSigningService("name") {
            override val aliases = emptyList<Alias>()
        }

        mockSecurity.verify({
            Security.addProvider(any())
        }, never())
    }

    @Test
    fun `init will add provider if one not exists`() {
        mockSecurity.`when`<Provider?> {
            Security.getProvider(DelegatedKeystoreProvider.PROVIDER_NAME)
        }.doReturn(null)
        object : DelegatedSigningService("name") {
            override val aliases = emptyList<Alias>()
        }

        mockSecurity.verify {
            Security.addProvider(any<DelegatedKeystoreProvider>())
        }
    }

    @Test
    fun `asKeyStore creates key store with the correct name`() {
        val service = object : DelegatedSigningService("name") {
            override val aliases = emptyList<Alias>()
        }
        val keyStore = mock<KeyStore>()
        mockStatic(KeyStore::class.java).use { mockKeyStore ->
            mockKeyStore.`when`<KeyStore> {
                KeyStore.getInstance("name")
            }.doReturn(keyStore)

            service.asKeyStore()

            mockKeyStore.verify {
                KeyStore.getInstance("name")
            }
        }
    }

    @Test
    fun `asKeyStore load the key store`() {
        val service = object : DelegatedSigningService("name") {
            override val aliases = emptyList<Alias>()
        }
        val keyStore = mock<KeyStore>()
        mockStatic(KeyStore::class.java).use { mockKeyStore ->
            mockKeyStore.`when`<KeyStore> {
                KeyStore.getInstance("name")
            }.doReturn(keyStore)

            service.asKeyStore()
        }

        verify(keyStore).load(null)
    }

    @Nested
    inner class FindOriginalSignatureProviderTest {
        @Test
        fun `findOriginalSignatureProvider returns the correct provider`() {
            val myFirstProvider = mock<DelegatedKeystoreProvider>()
            val mySecondProvider = mock<DelegatedSignatureProvider>()
            val noServicesProvider = mock<Provider> {
                on { services } doReturn emptySet()
            }
            val serviceWithWrongAlgorithm = mock<Provider.Service> {
                on { algorithm } doReturn "nop"
            }
            val serviceWithWrongType = mock<Provider.Service> {
                on { algorithm } doReturn "alg"
                on { type } doReturn "NOP"
            }
            val wrongProvider = mock<Provider> {
                on { services } doReturn setOf(serviceWithWrongAlgorithm, serviceWithWrongType)
            }
            val correctProvider = mock<Provider>()
            val correctService = mock<Provider.Service> {
                on { algorithm } doReturn "alg"
                on { type } doReturn "Signature"
                on { provider } doReturn correctProvider
            }
            whenever(correctProvider.services).doReturn(setOf(serviceWithWrongType, correctService))
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    myFirstProvider,
                    mySecondProvider,
                    noServicesProvider,
                    wrongProvider,
                    correctProvider
                )
            )
            val service = object : DelegatedSigningService("name") {
                override val aliases = emptyList<Alias>()
                fun provider() = findOriginalSignatureProvider("alg")
            }

            val provider = service.provider()

            assertThat(provider).isSameAs(correctProvider)
        }

        @Test
        fun `findOriginalSignatureProvider throws exception when it can't find a provider`() {
            val noServicesProvider = mock<Provider> {
                on { services } doReturn emptySet()
            }
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    noServicesProvider,
                )
            )
            val service = object : DelegatedSigningService("name") {
                override val aliases = emptyList<Alias>()
                fun provider() = findOriginalSignatureProvider("alg")
            }

            assertThrows<SecurityException> {
                service.provider()
            }
        }

        @Test
        fun `findOriginalSignatureProvider throws exception when provider is null`() {
            val correctService = mock<Provider.Service> {
                on { algorithm } doReturn "alg"
                on { type } doReturn "Signature"
                on { provider } doReturn null
            }
            val badProvider = mock<Provider> {
                on { services } doReturn setOf(correctService)
            }
            mockSecurity.`when`<Array<Provider>> {
                Security.getProviders()
            }.doReturn(
                arrayOf(
                    badProvider
                )
            )
            val service = object : DelegatedSigningService("name") {
                override val aliases = emptyList<Alias>()
                fun provider() = findOriginalSignatureProvider("alg")
            }

            assertThrows<SecurityException> {
                service.provider()
            }
        }
    }
}
