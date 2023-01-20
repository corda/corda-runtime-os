package net.corda.schema.configuration

object ReconciliationConfig {
        // Scheduled reconciliation tasks
        const val RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS = "permissionSummaryIntervalMs"
        const val RECONCILIATION_CPK_WRITE_INTERVAL_MS = "cpkWriteIntervalMs"
        const val RECONCILIATION_CPI_INFO_INTERVAL_MS = "cpiInfoIntervalMs"
        const val RECONCILIATION_CONFIG_INTERVAL_MS = "configIntervalMs"
        const val RECONCILIATION_VNODE_INFO_INTERVAL_MS ="vnodeInfoIntervalMs"
        const val RECONCILIATION_GROUP_PARAMS_INTERVAL_MS = "groupParamsIntervalMs"
        const val RECONCILIATION_MTLS_MGM_ALLOWED_LIST_INTERVAL_MS = "mtlsMgmAllowedCertificateSubjectsIntervalMs"
}