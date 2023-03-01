package net.corda.v5.ledger.common.transaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines the summary of the metadata of a Corda package (CPK or CPI).
 */
public interface CordaPackageSummary {

    /**
     * Gets the name of the package.
     *
     * @return Returns the name of the package.
     */
    @NotNull
    String getName();

    /**
     * Gets the version of the package.
     *
     * @return Returns the version of the package.
     */
    @NotNull
    String getVersion();

    /**
     * Gets the hash sum identifying the signer for CPIs, or null for CPKs.
     *
     * @return Returns the hash sum identifying the signer for CPIs, or null for CPKs.
     */
    @Nullable
    String getSignerSummaryHash();

    /**
     * Gets the checksum of the package file.
     *
     * @return Returns the checksum of the package file.
     */
    @NotNull
    String getFileChecksum();
}
