package net.corda.membership.client

import net.corda.crypto.core.ShortHash
import net.corda.v5.base.exceptions.CordaRuntimeException

class CouldNotFindMemberException(holdingIdentityShortHash: ShortHash) :
    CordaRuntimeException("Could not find member: $holdingIdentityShortHash")
