package net.corda.v5.crypto;

import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.Set;

/**
 * A tree data structure that enables the representation of composite public keys, which are used to represent
 * the signing requirements for multi-signature scenarios. A composite key is a list
 * of leaf keys and their contributing weight, and each leaf can be a conventional single key or a composite key.
 * Keys contribute their weight to the total if they are matched by the signature.
 * <p>
 * For complex scenarios, such as <em>"Both Alice and Bob need to sign to consume a state S"</em>, we can represent
 * the requirement by creating a tree with a root {@link CompositeKey}, and <code>Alice</code> and <code>Bob</code> as children.
 * The root node would specify <strong>weights</strong> for each of its children and a <strong>threshold</strong> –
 * the minimum total weight required (e.g. the minimum number of child signatures required) to satisfy the
 * tree signature requirement.
 * <p>
 * Using these constructs we can express e.g. 1 of N (OR) or N of N (AND) signature requirements. By nesting we can
 * create multi-level requirements such as <em>"either the CEO or 3 of 5 of his assistants need to sign"</em>.
 * <p>
 * Composite key implementations will track the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the subtree rooted at this node.
 */
public interface CompositeKey extends PublicKey {
    /**
     * This method will detect graph cycles in the full composite key structure to protect against infinite loops when
     * traversing the graph and key duplicates in each layer. It also checks if the threshold and weight constraint
     * requirements are met, while it tests for aggregated-weight integer overflow.
     * In practice, this method should be always invoked on the root {@link CompositeKey}, as it inherently
     * validates the child nodes (all the way till the leaves).
     */
    void checkValidity();

    /**
     * Takes single {@link PublicKey} and checks if {@link CompositeKey} requirements hold for that key.
     *
     * @param key the public key
     * @return true if the public key is a composite key, false otherwise
     */
    boolean isFulfilledBy(@NotNull PublicKey key);

    /**
     * Checks if the public keys corresponding to the signatures are matched against the leaves of the composite
     * key tree in question, and the total combined weight of all children is calculated for every intermediary node.
     * If all thresholds are satisfied, the composite key requirement is considered to be met.
     */
    boolean isFulfilledBy(@NotNull Iterable<PublicKey> keysToCheck);

    /**
     * Set of all leaf keys of that {@link CompositeKey}.
     *
     * @return a {@link Set} of the {@link PublicKey}
     */
    @NotNull
    Set<PublicKey> getLeafKeys();
}
