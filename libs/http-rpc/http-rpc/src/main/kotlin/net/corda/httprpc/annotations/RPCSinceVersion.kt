package net.corda.httprpc.annotations

/**
 * Records the protocol version in which this RPC method was added.
 *
 * In particular [net.corda.httprpc.client.HttpRpcClient] makes use of this annotation to guard against scenario when
 * HTTP RPC Client's [net.corda.httprpc.RestResource] interface been updated ahead of server side implementation.
 */
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class RPCSinceVersion(val version: Int)