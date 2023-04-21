package net.corda.membership.client

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.exceptions.CordaRuntimeException

class MemberNotAnMgmException(holdingIdentityShortHash: ShortHash) :
    CordaRuntimeException("Member: $holdingIdentityShortHash is not an MGM")
