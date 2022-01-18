package net.corda.utils.security.provider

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
}
