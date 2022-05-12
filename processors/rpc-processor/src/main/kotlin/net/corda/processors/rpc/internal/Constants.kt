package net.corda.processors.rpc.internal

import net.corda.schema.configuration.ConfigKeys.RPC_ADDRESS
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_DESCRIPTION
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_TITLE
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.schema.configuration.ConfigKeys.RPC_MAX_CONTENT_LENGTH

internal val CONFIG_HTTP_RPC =
    """$RPC_ADDRESS="0.0.0.0:8888"
        $RPC_CONTEXT_DESCRIPTION="Exposing RPCOps interfaces as OpenAPI WebServices"
        $RPC_CONTEXT_TITLE="HTTP RPC"
        $RPC_MAX_CONTENT_LENGTH=2000000000
        $RPC_ENDPOINT_TIMEOUT_MILLIS=12000""".trimIndent()

internal const val CLIENT_ID_RPC_PROCESSOR = "rpc.processor"