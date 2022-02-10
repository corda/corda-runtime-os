package net.corda.crypto.delegated.signing

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.Provider
import java.security.Security

class DelegatedSignerInstallerTest {
    private val mockSecurity = mockStatic(Security::class.java)
    private val installer = DelegatedSignerInstaller()

    @AfterEach
    fun cleanUp() {
        mockSecurity.close()
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
        val certificates = mock<DelegatedCertificateStore>()

        installer.install("name", signer, certificates)

        verify(provider).putServices("name", signer, certificates)
    }
}
