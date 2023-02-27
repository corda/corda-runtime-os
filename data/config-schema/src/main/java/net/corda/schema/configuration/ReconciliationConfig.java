package net.corda.schema.configuration;

public final class ReconciliationConfig {
    private ReconciliationConfig() {
    }

    // Scheduled reconciliation tasks
    public static final String RECONCILIATION_PERMISSION_SUMMARY_INTERVAL_MS = "permissionSummaryIntervalMs";
    public static final String RECONCILIATION_CPK_WRITE_INTERVAL_MS = "cpkWriteIntervalMs";
    public static final String RECONCILIATION_CPI_INFO_INTERVAL_MS = "cpiInfoIntervalMs";
    public static final String RECONCILIATION_CONFIG_INTERVAL_MS = "configIntervalMs";
    public static final String RECONCILIATION_VNODE_INFO_INTERVAL_MS ="vnodeInfoIntervalMs";
    public static final String RECONCILIATION_GROUP_PARAMS_INTERVAL_MS = "groupParamsIntervalMs";
    public static final String RECONCILIATION_MTLS_MGM_ALLOWED_LIST_INTERVAL_MS = "mtlsMgmAllowedCertificateSubjectsIntervalMs";
}
