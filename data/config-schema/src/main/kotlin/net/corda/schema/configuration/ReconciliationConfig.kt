package net.corda.schema.configuration

object ReconciliationConfig {
        // Scheduled reconciliation tasks
        const val RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS = "permissionSummaryIntervalMs"
        const val RECONCILIATION_CPK_WRITE_INTERVAL_MS = "cpkWriteIntervalMs"
        const val RECONCILIATION_CPI_INFO_INTERVAL_MS = "cpiInfoIntervalMs"
}