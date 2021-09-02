package net.corda.install

import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.packaging.PackagingException
import net.corda.v5.crypto.SecureHash
import java.io.InputStream
import java.nio.file.Path
import java.util.*

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
     * Creates a [Cpb.Expanded] from the [inputStream], verifies the [Cpb], parses its [Cpk]s, and stores them for the
     * creation of sandboxes and CorDapps in the future.
     *
     * A [PackagingException] is thrown if the [inputStream] contains an invalid [Cpb].
     * A [CpkInstallationException] is thrown if any of the CPB fails to install.
     * A [CpkVerificationException] is thrown if any of the CPBs fail to verify.
     *
     * @return The [Cpb]s parsed from the [inputStream]
     */
    fun loadCpb(inputStream : InputStream): Cpb.Expanded

    /**
     * Verifies the [Cpk] retrieved from the [inputStream] and identified by [cpkHash], and stores it for retrieval
     * when creating future sandboxes.
     *
     * A [PackagingException] is thrown if the [inputStream] contains an invalid [Cpk].
     * A [CpkInstallationException] is thrown if any of the CPKs fails to install.
     * A [CpkVerificationException] is thrown if any of the CPKs fail to verify.
     */
    fun loadCpk(cpkHash : SecureHash, inputStream : InputStream) : Cpk.Expanded

    /** Returns the stored [Cpb.Expanded] for the CPB identified by [cpbIdentifier], or null if no such CPB has been stored. */
    fun getCpb(cpbIdentifier: Cpb.Identifier): Cpb.Expanded?

    /** Returns the stored [Cpk.Expanded] identified by [id], or null if no such CPK has been stored. */
    fun getCpk(id : Cpk.Identifier): Cpk.Expanded?

    /** Returns the stored [Cpk.Expanded] identified by [cpkHash], or null if no such CPK has been stored. */
    fun getCpk(cpkHash: SecureHash): Cpk.Expanded?

    /**
     * Verifies a set of CPKs as a group.
     *
     * Does not run verification of the individual CPKs, since this is performed when the CPK is installed.
     *
     * [Cpb]s are also verified as a group at installation time, and thus a CPB's CPKs don't need to be re-verified.
     */
    fun verifyCpkGroup(cpks: Iterable<Cpk>)

    fun getCpbIdentifiers() : Set<Cpb.Identifier>

    fun hasCpk(cpkHash: SecureHash): Boolean
}