package net.corda.flow.utils

import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity

/**
 * These utility functions will be replaced soon when Checkpoint wrapper is implemented.
 */
fun HoldingIdentity.getMemberX500Name(): MemberX500Name {
    try {
        return MemberX500Name.parse(x500Name)
    } catch (e: Exception) {
        throw IllegalStateException(
            "Failed to convert Holding Identity x500 name '${x500Name}' to MemberX500Name",
            e
        )
    }
}