package net.corda.v5.crypto.merkle

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * Indicates that the calculation of the root hash of a [MerkleProof] failed.
 */
class MerkleProofRebuildFailureException(message: String) : CordaRuntimeException(message, null)