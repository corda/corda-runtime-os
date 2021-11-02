package net.corda.install

import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.packaging.PackagingException
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path

/**
 * Provides an interface for the installation of CPKs, both at start-up and as part of transaction resolution, and the
 * installation of drivers.
 */
interface InstallService {
    /**
     * Searches the [driverDirectories] for JARs (sub-folders are not searched), and installs them as bundles.
     *
     * A [DriverInstallationException] is thrown if a bundle fails to install or start, unless the failure is because
     * a bundle with the same symbolic name and version is already installed.
     */
    fun installDrivers(driverDirectories: Collection<Path>)

    /**
     * Used when installing new CPBs on the node.
     *
     * Creates a [CPI] from the [inputStream], verifies the [CPI], parses its [CPK]s, and stores them for the
     * creation of sandboxes and CorDapps in the future.
     *
     * A [PackagingException] is thrown if the [inputStream] contains an invalid [CPI].
     * A [CpkInstallationException] is thrown if any of the CPB fails to install.
     * A [CpkVerificationException] is thrown if any of the CPBs fail to verify.
     *
     * @return The [CPI]s parsed from the [inputStream]
     */
    fun loadCpb(inputStream : InputStream): CPI

    /**
     * Verifies the [CPK] retrieved from the [inputStream] and identified by [cpkHash], and stores it for retrieval
     * when creating future sandboxes.
     *
     * A [PackagingException] is thrown if the [inputStream] contains an invalid [CPK].
     * A [CpkInstallationException] is thrown if any of the CPKs fails to install.
     * A [CpkVerificationException] is thrown if any of the CPKs fail to verify.
     */
    fun loadCpk(cpkHash : SecureHash, inputStream : InputStream) : CPK

    /** Returns the stored [CPI] for the CPI identified by [cpiIdentifier], or null if no such CPI has been stored. */
    fun getCpb(cpiIdentifier: CPI.Identifier): CPI?

    /** Returns the stored [CPK] identified by [id], or null if no such CPK has been stored. */
    fun getCpk(id : CPK.Identifier): CPK?

    /** Returns the stored [CPK] identified by [cpkHash], or null if no such CPK has been stored. */
    fun getCpk(cpkHash: SecureHash): CPK?

    /**
     * Verifies a set of CPKs as a group.
     *
     * Does not run verification of the individual CPKs, since this is performed when the CPK is installed.
     *
     * [Cpb]s are also verified as a group at installation time, and thus a CPB's CPKs don't need to be re-verified.
     */
    fun verifyCpkGroup(cpks: Iterable<CPK>)

    fun getCpbIdentifiers() : Set<CPI.Identifier>

    fun hasCpk(cpkHash: SecureHash): Boolean
}