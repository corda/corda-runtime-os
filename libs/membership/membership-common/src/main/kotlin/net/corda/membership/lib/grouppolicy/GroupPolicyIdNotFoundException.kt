package net.corda.membership.lib.grouppolicy

import net.corda.v5.base.exceptions.CordaRuntimeException

class GroupPolicyIdNotFoundException : CordaRuntimeException("group policy ID not found")
