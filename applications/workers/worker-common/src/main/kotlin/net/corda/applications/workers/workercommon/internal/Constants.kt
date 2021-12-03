package net.corda.applications.workers.workercommon.internal

internal const val HEALTH_MONITOR_PORT = 7000
internal const val HTTP_OK_CODE = 200
internal const val HTTP_INTERNAL_SERVER_ERROR_CODE = 500
internal const val HTTP_HEALTH_ROUTE = "/isHealthy"
internal const val HTTP_READINESS_ROUTE = "/isReady"
internal const val PARAM_INSTANCE_ID = "--instanceId"
internal const val PARAM_DISABLE_HEALTH_MONITOR = "--disableHealthMonitor"
internal const val PARAM_HEALTH_MONITOR_PORT = "--healthMonitorPort"
internal const val PARAM_EXTRA = "-c"