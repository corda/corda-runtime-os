package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.ShortHash

class MemberNotAnMgmException(holdingIdentityShortHash: ShortHash) :
    CordaRuntimeException("Member: $holdingIdentityShortHash is not an MGM")
