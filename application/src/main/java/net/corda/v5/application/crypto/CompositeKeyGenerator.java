package net.corda.v5.application.crypto;

import net.corda.v5.crypto.CompositeKey;
import net.corda.v5.crypto.CompositeKeyNodeAndWeight;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.PublicKey;
import java.util.List;


/**
 * A generator for a tree data structure that enables the representation of composite public keys,
 * which are used to represent the signing requirements for multi-signature scenarios. A composite key is a list
 * of leaf keys and their contributing weight, and each leaf can be a conventional single key or a composite key.
 * Keys contribute their weight to the total if they are matched by the signature.
 * <p>
 * For complex scenarios, such as *"Both Alice and Bob need to sign to consume a state S"*, we can represent
 * the requirement by creating a tree with a root {@link CompositeKey}, and Alice and Bob as children.
 * The root node would specify *weights* for each of its children and a *threshold* – the minimum total weight required
 * (e.g. the minimum number of child signatures required) to satisfy the tree signature requirement.
 * <p>
 * Using these constructs we can express e.g. 1 of N (OR) or N of N (AND) signature requirements. By nesting we can
 * create multi-level requirements such as *"either the CEO or 3 of 5 of his assistants need to sign"*.
 * <p>
 * Composite key implementations will track the minimum total weight required (in the simple case – the minimum number of child
 * signatures required) to satisfy the subtree rooted at this node.
 */

public interface CompositeKeyGenerator {
    /**
     * Return a composite key from a weighted list of keys, and an overall threshold
     *
     * @param keys A list of keys, each which its own weight
     * @param threshold The threshold of total weights of keys that can be validated.
     */
    @NotNull
    PublicKey create(@NotNull List<CompositeKeyNodeAndWeight> keys, @Nullable Integer threshold);
}
