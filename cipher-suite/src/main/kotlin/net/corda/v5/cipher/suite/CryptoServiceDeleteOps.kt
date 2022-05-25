package net.corda.v5.cipher.suite

import net.corda.v5.crypto.exceptions.CryptoServiceException

/**
 * The optional interface which can be implemented by HSMs which support key deletion.
 */
interface CryptoServiceDeleteOps {
    /**
     * Deletes the key corresponding to the input alias.
     * This method doesn't throw if the alias is not found.
     *
     * @throws CryptoServiceException if the key cannot be removed.
     */
    fun delete(alias: String)
}
