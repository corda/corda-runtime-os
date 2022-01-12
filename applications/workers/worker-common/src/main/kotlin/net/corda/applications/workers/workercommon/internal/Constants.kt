package net.corda.applications.workers.workercommon.internal

internal const val HTTP_OK_CODE = 200
internal const val HTTP_INTERNAL_SERVER_ERROR_CODE = 500
internal const val HTTP_HEALTH_ROUTE = "/isHealthy"
internal const val HTTP_READINESS_ROUTE = "/isReady"
internal const val HEALTH_MONITOR_PORT = 7000

internal const val INSTANCE_ID_PATH = "instanceId"
// Same as `net.corda.messaging.kafka.properties.ConfigProperties.TOPIC_PREFIX`
internal const val TOPIC_PREFIX_PATH = "topic.prefix"
internal const val MSG_CONFIG_PATH = "messaging"
internal const val CUSTOM_CONFIG_PATH = "custom"