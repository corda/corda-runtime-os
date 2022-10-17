package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class CouldNotFindMemberException : CordaRuntimeException("Could not find member")
