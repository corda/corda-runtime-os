package net.corda.membership.persistence.client

import net.corda.virtualnode.HoldingIdentity

fun interface GroupPolicyListener {
    fun onUpdate(holdingIdentity: HoldingIdentity)
}