package net.corda.v5.application.flows

/**
 * Marks a constructor of a flow indicating that it can be instantiated from a JSON String.
 * Flows that are available to start via the RPC service with [net.corda.client.rpc.flow.RpcStartFlowRequestParameters] must have a
 * constructor with this annotation.
 */
@Target(AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonConstructor