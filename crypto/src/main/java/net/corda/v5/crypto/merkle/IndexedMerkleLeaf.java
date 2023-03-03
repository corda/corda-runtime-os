package net.corda.v5.crypto.merkle;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * {@link IndexedMerkleLeaf}s are building blocks of {@link MerkleProof}s.
 * They contain the required information about a particular leaf which is needed for the verification.
 */
@CordaSerializable
public final class IndexedMerkleLeaf {
    private final int index;
    
    private final byte[] nonce;
    
    private final byte[] leafData;

    public IndexedMerkleLeaf(int index, @Nullable byte[] nonce, @NotNull byte[] leafData) {
        this.index = index;
        this.nonce = nonce;
        this.leafData = leafData;
    }

    @NotNull
    public String toString() {
        return "Leaf(" + this.index + ")[" + this.leafData.length + " bytes]";
    }

    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null) return false;
        if (!(other instanceof IndexedMerkleLeaf)) return false;

        IndexedMerkleLeaf otherLeaf = (IndexedMerkleLeaf) other;

        if (index != otherLeaf.index) return false;
        if (nonce != otherLeaf.nonce) {
            if (otherLeaf.nonce == null) return false;
            if (!Arrays.equals(nonce, otherLeaf.nonce)) return false;
        } else if (otherLeaf.nonce != null) return false;
        return Arrays.equals(leafData, otherLeaf.leafData);
    }

    public int hashCode() {
        int result = index;
        if (nonce != null) result = 31 * result + Arrays.hashCode(nonce);
        result = 31 * result + Arrays.hashCode(leafData);
        return result;
    }

    /**
     * Obtain the index of this leaf
     *
     * @return integer leaf index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Obtain the nonce of this tree leaf
     *
     * @return nonce as a byte array
     */
    @Nullable
    public byte[] getNonce() {
        return nonce;
    }

    /**
     * Obtain the data for this leaf.
     *
     * @return leaf data as a byte array
     */
    @NotNull
    public byte[] getLeafData() {
        return leafData;
    }
}