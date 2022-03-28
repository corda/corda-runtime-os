package net.corda.flow.utils

import net.corda.data.flow.state.Checkpoint
import net.corda.data.identity.HoldingIdentity
import net.corda.v5.base.types.MemberX500Name

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

fun Checkpoint.getHoldingIdentity(): HoldingIdentity {
    checkNotNull(flowStartContext){"Failed to get virtual Node x500 name, checkpoint.flowStartContext is null"}
    checkNotNull(flowStartContext.identity){"Failed to get virtual Node x500 name, checkpoint.flowStartContext.identity is null"}
    return this.flowStartContext.identity
}