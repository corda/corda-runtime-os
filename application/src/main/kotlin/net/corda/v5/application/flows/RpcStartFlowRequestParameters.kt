package net.corda.v5.application.flows

import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
data class RpcStartFlowRequestParameters(val parametersInJson: String)