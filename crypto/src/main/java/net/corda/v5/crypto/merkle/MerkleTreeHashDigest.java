package net.corda.v5.crypto.merkle;

import net.corda.v5.crypto.DigestAlgorithmName;
import org.jetbrains.annotations.NotNull;

/**
 * Different use cases require different {@link MerkleTree} calculations.
 * {@link MerkleTreeHashDigest}s let us specify such implementations.
 */
public interface MerkleTreeHashDigest {
    
    /**
     * Get the algorithm name
     *
     * @return Specifies the digest algorithm.
     */
    @NotNull
    DigestAlgorithmName getDigestAlgorithmName();
}
