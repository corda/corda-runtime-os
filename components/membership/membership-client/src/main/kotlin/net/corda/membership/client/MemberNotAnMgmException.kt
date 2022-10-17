package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException

class MemberNotAnMgmException : CordaRuntimeException("Member is not an MGM")
