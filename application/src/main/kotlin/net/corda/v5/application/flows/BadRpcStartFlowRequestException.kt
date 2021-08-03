package net.corda.v5.application.flows

import net.corda.v5.base.exceptions.CordaRuntimeException

/**
 * This exception is meant to be thrown by CorDapps constructor annotated with [JsonConstructor] which takes [RpcStartFlowRequestParameters]
 * when incoming JSON string cannot be used to construct a flow.
 */
class BadRpcStartFlowRequestException(val msg: String) : CordaRuntimeException(msg)