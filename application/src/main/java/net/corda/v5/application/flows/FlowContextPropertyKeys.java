package net.corda.v5.application.flows;

public final class FlowContextPropertyKeys {

    private FlowContextPropertyKeys() {
    }

    public static final String CPI_NAME = "corda.cpiName";
    public static final String CPI_VERSION = "corda.cpiVersion";
    public static final String CPI_SIGNER_SUMMARY_HASH = "corda.cpiSignerSummaryHash";
    public static final String CPI_FILE_CHECKSUM = "corda.cpiFileChecksum";
    public static final String CPK_FILE_CHECKSUM = "corda.cpkFileChecksum";
    public static final String INITIAL_PLATFORM_VERSION = "corda.initialPlatformVersion";
    public static final String INITIAL_SOFTWARE_VERSION = "corda.initialSoftwareVersion";
}
