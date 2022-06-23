package net.corda.v5.ledger.obsolete.merkle

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.exceptions.CordaRuntimeException

@CordaSerializable
class MerkleTreeException(val reason: String) : CordaRuntimeException("Merkle Tree exception. Reason: $reason")
