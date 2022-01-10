package net.corda.processors.rpc.internal

internal const val CONFIG_CLIENT_RPC_PROCESSOR = "config.manager.rpc.processor"
internal const val CONFIG_TOPIC = "ConfigTopic"
internal const val CONFIG_KEY_CONFIG_TOPIC_NAME = "config.topic.name"
internal val CONFIG_HTTP_RPC = """
            address="0.0.0.0:8888"
            context.description="Exposing RPCOps interfaces as OpenAPI WebServices"
            context.title="HTTP RPC demo"
        """.trimIndent()
internal const val CONFIG_CONFIG_MGMT_REQUEST_TIMEOUT = "timeout.millis=\"10000\""