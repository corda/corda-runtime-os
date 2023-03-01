package net.corda.v5.ledger.common.transaction;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * Defines a mechanism for implementing a privacy salt.
 * A privacy salt is required to compute  a nonce per transaction component in order to ensure that an adversary cannot
 * use brute force techniques and reveal the content of a Merkle-leaf hashed value.
 */
@CordaSerializable
@DoNotImplement
public interface PrivacySalt {

    /**
     * Gets the value of the privacy salt.
     *
     * @return Returns the value of the privacy salt.
     */
    @NotNull
    byte[] getBytes();
}
