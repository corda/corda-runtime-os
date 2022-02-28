package net.corda.processors.rpc.internal

import net.corda.schema.configuration.ConfigKeys.RPC_ADDRESS
import net.corda.schema.configuration.ConfigKeys.RPC_AZUREAD_CLIENT_ID
import net.corda.schema.configuration.ConfigKeys.RPC_AZUREAD_TENANT_ID
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_DESCRIPTION
import net.corda.schema.configuration.ConfigKeys.RPC_CONTEXT_TITLE
import net.corda.schema.configuration.ConfigKeys.RPC_ENDPOINT_TIMEOUT_MILLIS
import net.corda.schema.configuration.ConfigKeys.RPC_MAX_CONTENT_LENGTH

internal val CONFIG_HTTP_RPC =
    """$RPC_ADDRESS="0.0.0.0:8888"
        $RPC_CONTEXT_DESCRIPTION="Exposing RPCOps interfaces as OpenAPI WebServices"
        $RPC_CONTEXT_TITLE="HTTP RPC"
        $RPC_AZUREAD_CLIENT_ID="f4a37d97-c561-4367-97fd-b10d44ceae24"
        $RPC_AZUREAD_TENANT_ID="a4be1f2e-2d10-4195-87cd-736aca9b672c"
        $RPC_MAX_CONTENT_LENGTH=2000000000
        $RPC_ENDPOINT_TIMEOUT_MILLIS=12000""".trimIndent()

internal const val CLIENT_ID_RPC_PROCESSOR = "rpc.processor"