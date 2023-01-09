package net.corda.membership.group.policy.validation

import net.corda.v5.base.exceptions.CordaRuntimeException

open class MembershipInvalidGroupPolicyException(
    message: String,
    cause: Throwable? = null,
): CordaRuntimeException(message, cause)