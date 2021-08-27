package net.corda.v5.httprpc.api.annotations

/** Records the protocol version in which this RPC was added. */
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class RPCSinceVersion(val version: Int)