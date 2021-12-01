package net.corda.applications.workers.healthprovider.internal

internal const val HEALTH_CHECK_PATH_NAME = "worker_is_healthy"
internal const val READINESS_CHECK_PATH_NAME = "worker_is_ready"
internal const val HTTP_HEALTH_PROVIDER_PORT = 7000
internal const val HTTP_OK_CODE = 200
internal const val HTTP_INTERNAL_SERVER_ERROR_CODE = 500
internal const val HTTP_HEALTH_ROUTE = "/isHealthy"
internal const val HTTP_READINESS_ROUTE = "/isReady"