package net.corda.p2p.linkmanager

import net.corda.crypto.client.CryptoOpsClient
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.lifecycle.domino.logic.NamedLifecycle
import net.corda.p2p.test.stub.crypto.processor.CryptoProcessor
import net.corda.v5.crypto.SignatureSpec
import java.security.PublicKey

class DelegatingCryptoService(private val cryptoOpsClient: CryptoOpsClient): CryptoProcessor {

    override fun sign(tenantId: String, publicKey: PublicKey, spec: SignatureSpec, data: ByteArray): ByteArray {
        return cryptoOpsClient.sign(tenantId, publicKey, spec, data).bytes
    }

    override val namedLifecycle: NamedLifecycle = NamedLifecycle(
        cryptoOpsClient,
        LifecycleCoordinatorName.forComponent<CryptoOpsClient>()
    )
}