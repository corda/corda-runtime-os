package net.corda.p2p.gateway.messaging

import net.corda.crypto.delegated.signing.DelegatedCertificateStore
import net.corda.crypto.delegated.signing.DelegatedSigner
import net.corda.crypto.delegated.signing.DelegatedSignerInstaller
import java.security.KeyStore
import java.util.UUID

internal class KeyStoreFactory(
    private val signer: DelegatedSigner,
    private val certificatesStore: DelegatedCertificateStore,
    // Using unique name to allow us to use two different key stores on the same VM
    // (Used in the integration tests)
    private val name: String = "Gateway-JKS-Signing-Service-${UUID.randomUUID()}",
    private val installer: DelegatedSignerInstaller = DelegatedSignerInstaller(),
) {
    fun createKeyStore(): KeyStore {
        installer.install(name, signer, certificatesStore)
        return KeyStore.getInstance(name).also {
            it.load(null)
        }
    }
}
