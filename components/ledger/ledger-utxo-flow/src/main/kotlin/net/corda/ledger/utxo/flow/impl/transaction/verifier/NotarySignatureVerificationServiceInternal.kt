package net.corda.ledger.utxo.flow.impl.transaction.verifier

import net.corda.v5.crypto.SecureHash
import net.corda.v5.ledger.utxo.NotarySignatureVerificationService
import java.security.PublicKey

interface NotarySignatureVerificationServiceInternal : NotarySignatureVerificationService {
    fun getNotaryPublicKeyByKeyId(
        keyId: SecureHash,
        notaryKey: PublicKey,
        keyIdToNotaryKeys: MutableMap<String, Map<SecureHash, PublicKey>>
    ): PublicKey?

    fun getKeyOrLeafKeys(publicKey: PublicKey): List<PublicKey>
}
