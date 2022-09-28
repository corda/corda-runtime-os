package net.corda.libs.virtualnode.common.exception

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * HoldingIdentity not found.
 */
class HoldingIdentityNotFoundException(holdingIdentityShortHash: String) :
    CordaRuntimeException("Holding identity $holdingIdentityShortHash not found.")