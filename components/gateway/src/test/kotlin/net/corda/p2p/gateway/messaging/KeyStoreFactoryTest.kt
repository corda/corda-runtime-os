package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.security.KeyStore

class KeyStoreFactoryTest {
    private val keyStore = mock<KeyStore>()
    private val mockKeyStore = mockStatic(KeyStore::class.java).also {
        it.`when`<KeyStore> {
            KeyStore.getInstance("name")
        }.doReturn(keyStore)
    }
    private val signer = mock<DelegatedSigner>()
    private val certificateStore = mock<DelegatedCertificateStore>()
    private val installer = mock<DelegatedSignerInstaller>()
    private val testObject = KeyStoreFactory(
        signer,
        certificateStore,
        "name",
        installer
    )

    @AfterEach
    fun cleanUp() {
        mockKeyStore.close()
    }

    @Test
    fun `createKeyStore return the key store`() {
        assertThat(testObject.createKeyStore()).isSameAs(keyStore)
    }

    @Test
    fun `createKeyStore call the installer`() {
        testObject.createKeyStore()

        verify(installer).install("name", signer, certificateStore)
    }

    @Test
    fun `createKeyStore call load with null`() {
        testObject.createKeyStore()

        verify(keyStore).load(null)
    }
}
