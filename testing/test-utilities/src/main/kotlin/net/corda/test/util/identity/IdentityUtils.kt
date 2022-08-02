package net.corda.test.util.identity

import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

/**
 * Helper method to create holding identities with X500 name represented as string.
 * In regular code, the default constructor should be used as the `MemberX500Name` is encouraged, instead of the string type.
 */
fun createTestHoldingIdentity(x500Name: String, groupId: String): HoldingIdentity {
    return HoldingIdentity(MemberX500Name.parse(x500Name), groupId)
}