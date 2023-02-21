package net.corda.membership.lib.grouppolicy

import net.corda.virtualnode.HoldingIdentity

interface InteropGroupPolicyReader {
    fun getGroupPolicy(holdingIdentity: HoldingIdentity): String?
}