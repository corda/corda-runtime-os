package net.corda.membership.client

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.virtualnode.ShortHash

class CouldNotFindMemberException(holdingIdentityShortHash: ShortHash) :
    CordaRuntimeException("Could not find member: $holdingIdentityShortHash")
