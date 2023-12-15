package net.corda.rest.annotations

/**
 * Records the protocol version in which this REST method was added.
 *
 * In particular [net.corda.rest.client.RestClient] makes use of this annotation to guard against scenario when
 * HTTP REST Client's [net.corda.rest.RestResource] interface been updated ahead of server side implementation.
 */
@Target(AnnotationTarget.FUNCTION)
@MustBeDocumented
annotation class RestSinceVersion(val version: Int)
