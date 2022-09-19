package net.corda.ledger.utxo.impl

import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.ledger.utxo.CpkConstraint
import net.corda.v5.ledger.utxo.CpkConstraintContext

/**
 * Represents a CPK constraint that is always accepted.
 */
object AlwaysAcceptCpkConstraint : CpkConstraint {

    /**
     * Determines whether the constraint is satisfied by the specified context.
     *
     * @param digestService The digest service to use for constraint checking.
     * @param context The context in which to check the current constraint.
     * @return Returns true if the constraint is satisfied by the specified context; otherwise, false.
     */
    override fun isSatisfiedBy(digestService: DigestService, context: CpkConstraintContext) = true
}
