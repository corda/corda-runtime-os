package net.corda.p2p.linkmanager

import net.corda.v5.base.exceptions.CordaRuntimeException

interface LinkManagerCryptoService {

    /**
     * Sign [data] with the private-key corresponding to the [hash] of the public-key.
     * If the corresponding private-key cannot be found then this method throws a [NoPrivateKeyForGroupException]
     */
    fun signData(hash: ByteArray, data: ByteArray): ByteArray

    class NoPrivateKeyForGroupException(hash: String?):
        CordaRuntimeException("Could not find (our) private key in the network map corresponding to private key hash = $hash")
}