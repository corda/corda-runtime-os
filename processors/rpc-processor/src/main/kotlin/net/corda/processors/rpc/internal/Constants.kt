package net.corda.processors.rpc.internal

import net.corda.schema.configuration.ConfigKeys.Companion.RPC_ENDPOINT_TIMEOUT_MILLIS

internal val CONFIG_HTTP_RPC =
    """address="0.0.0.0:8888"
        context.description="Exposing RPCOps interfaces as OpenAPI WebServices"
        context.title="HTTP RPC"
        sso.azureAd.clientId="f4a37d97-c561-4367-97fd-b10d44ceae24"
        sso.azureAd.tenantId="a4be1f2e-2d10-4195-87cd-736aca9b672c"
        maxContentLength=2000000000
        $RPC_ENDPOINT_TIMEOUT_MILLIS=12000""".trimIndent()

internal const val CLIENT_ID_RPC_PROCESSOR = "rpc.processor"