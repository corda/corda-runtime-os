package net.corda.membership.lib.group.policy.validation

import net.corda.v5.base.exceptions.CordaRuntimeException

open class MembershipInvalidGroupPolicyException(
    message: String,
    cause: Throwable? = null,
): CordaRuntimeException(message, cause)