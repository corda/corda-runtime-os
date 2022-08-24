package net.corda.v5.ledger.obsolete.contracts

import net.corda.v5.base.annotations.CordaSerializable
import net.corda.v5.base.annotations.DoNotImplement
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.crypto.SecureHash

/** Constrain which contract-code-containing CPK can be used with a [Contract]. */
@CordaSerializable
@DoNotImplement
interface CPKConstraint {
    fun isSatisfiedBy(digestService: DigestService, context: CPKConstraintContext): Boolean
}

/** The necessary context to determine whether a [CPKConstraint] is satisfied. */
@DoNotImplement
interface CPKConstraintContext

/** Constrains which CPK can be used with a contract using the hashes of the public keys that signed the CPK. */
@DoNotImplement
interface SignatureCPKConstraint: CPKConstraint {
    val key: SecureHash
}

object AlwaysAcceptCPKConstraint : CPKConstraint {
    override fun isSatisfiedBy(digestService: DigestService, context: CPKConstraintContext) = true
}

object AutomaticPlaceholderCPKConstraint : CPKConstraint {
    override fun isSatisfiedBy(digestService: DigestService, context: CPKConstraintContext): Boolean {
        throw UnsupportedOperationException("Contracts cannot be satisfied by an AutomaticPlaceholderCPKConstraint placeholder.")
    }
}