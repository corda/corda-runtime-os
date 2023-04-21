package net.corda.v5.ledger.utxo;

import net.corda.v5.base.annotations.DoNotImplement;
import net.corda.v5.base.annotations.Suspendable;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;

/**
 * Provides functionality to determine state visibility.
 */
@DoNotImplement
public interface VisibilityChecker {

    /**
     * Determines whether any of the specified keys belong to the current node.
     *
     * @param keys The keys for which to determine belong to the current node.
     * @return Returns true if any of the specified keys belong to the current node; otherwise, false.
     */
    @Suspendable
    boolean containsMySigningKeys(@NotNull Iterable<PublicKey> keys);
}
