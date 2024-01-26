package net.corda.crypto.cipher.suite.merkle

import net.corda.v5.crypto.extensions.merkle.MerkleTreeHashDigestProvider
import net.corda.v5.crypto.merkle.MerkleProof

interface MerkleProofInternal : MerkleProof {
    fun merge(other: MerkleProof, digest: MerkleTreeHashDigestProvider): MerkleProof
}
