package net.corda.membership.lib.grouppolicy

import net.corda.v5.base.exceptions.CordaRuntimeException

class GroupPolicyParseException(_message: String, _cause: Throwable) : CordaRuntimeException(_message, _cause)
