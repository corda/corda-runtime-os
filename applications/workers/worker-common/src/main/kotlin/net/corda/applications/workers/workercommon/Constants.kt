package net.corda.applications.workers.workercommon

internal const val HTTP_OK_CODE = 200
internal const val HTTP_SERVICE_UNAVAILABLE_CODE = 503
internal const val HTTP_HEALTH_ROUTE = "/isHealthy"
internal const val HTTP_METRICS_ROUTE = "/metrics"
internal const val HTTP_STATUS_ROUTE = "/status"
internal const val WORKER_SERVER_PORT = 7000 // Note: This variable is defined in charts/corda-lib/templates/_helpers.kt:622
internal const val NO_CACHE = "no-cache"
