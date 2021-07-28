package net.corda.v5.crypto.exceptions

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class MerkleTreeException(val reason: String) : CryptoServiceLibraryException("Merkle Tree exception. Reason: $reason")
