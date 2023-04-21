package net.corda.v5.crypto.merkle;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link IndexedMerkleLeaf}s are building blocks of {@link MerkleProof}s.
 * They contain the required information about a particular leaf which is needed for the verification.
 */
@DoNotImplement
@CordaSerializable
public interface IndexedMerkleLeaf {

    /**
     * Obtain the index of this leaf.
     *
     * @return Integer leaf index.
     */
    public int getIndex();

    /**
     * Obtain the nonce of this tree leaf.
     *
     * @return Nonce as a byte array.
     */
    @Nullable
    public byte[] getNonce();

    /**
     * Obtain the data for this leaf.
     *
     * @return Leaf data as a byte array.
     */
    @NotNull
    public byte[] getLeafData();
}