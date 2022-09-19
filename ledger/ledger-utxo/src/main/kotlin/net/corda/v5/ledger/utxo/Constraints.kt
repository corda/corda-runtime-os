package net.corda.v5.ledger.utxo

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.DigestService

/**
 * Defines a constraint that determines which contract-code-containing CPK can be used with a [Contract].
 */
@CordaSerializable
@DoNotImplement
interface CpkConstraint {

    /**
     * Determines whether the constraint is satisfied by the specified context.
     *
     * @param digestService The digest service to use for constraint checking.
     * @param context The context in which to check the current constraint.
     * @return Returns true if the constraint is satisfied by the specified context; otherwise, false.
     */
    fun isSatisfiedBy(digestService: DigestService, context: CpkConstraintContext): Boolean
}

/**
 * Defines the context by which to determine whether a [CpkConstraint] is satisfied.
 */
@DoNotImplement
interface CpkConstraintContext