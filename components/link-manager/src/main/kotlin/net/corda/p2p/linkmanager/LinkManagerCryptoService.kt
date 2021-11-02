package net.corda.p2p.linkmanager

import net.corda.lifecycle.domino.logic.LifecycleWithDominoTile
import net.corda.v5.base.exceptions.CordaRuntimeException
import java.security.PublicKey

interface LinkManagerCryptoService: LifecycleWithDominoTile {

    /**
     * Sign [data] with the private-key corresponding to the [hash] of the public-key.
     * If the corresponding private-key cannot be found then this method throws a [NoPrivateKeyForGroupException]
     */
    fun signData(publicKey: PublicKey, data: ByteArray): ByteArray

    class NoPrivateKeyForGroupException(publicKey: PublicKey):
        CordaRuntimeException("Could not find the private key corresponding to public key $publicKey")
}