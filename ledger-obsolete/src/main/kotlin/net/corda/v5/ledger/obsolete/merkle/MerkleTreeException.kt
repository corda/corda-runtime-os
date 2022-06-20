package net.corda.v5.ledger.obsolete.merkle

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException

@CordaSerializable
class MerkleTreeException(val reason: String) : CryptoServiceLibraryException("Merkle Tree exception. Reason: $reason")
