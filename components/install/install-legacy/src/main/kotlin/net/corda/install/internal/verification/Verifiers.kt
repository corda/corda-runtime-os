package net.corda.install.internal.verification

import aQute.bnd.header.OSGiHeader
import net.corda.install.CpkVerificationException
import net.corda.install.internal.CONFIG_ADMIN_BLACKLISTED_KEYS
import net.corda.install.internal.CONFIG_ADMIN_PLATFORM_VERSION
import net.corda.install.internal.SUPPORTED_CPK_FORMATS
import net.corda.packaging.CordappManifest
import net.corda.packaging.CordappManifest.Companion.DEFAULT_MIN_PLATFORM_VERSION
import net.corda.packaging.CPK
import net.corda.packaging.DependencyResolutionException
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.create
import net.corda.v5.crypto.sha256Bytes
import org.osgi.framework.Constants
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * CPK format verifier - Checks that all of the CPKs are on a supported CPK format.
 */
@Component
internal class CpkFormatVerifier : StandaloneCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) {
        cpks.forEach { cpk ->
            val cpkFormat = cpk.manifest.cpkFormatVersion
            if (cpk.manifest.cpkFormatVersion !in SUPPORTED_CPK_FORMATS) {
                throw CpkVerificationException("CPK ${cpk.id} is on version ${cpkFormat}. " +
                        "The supported CPK versions are: $SUPPORTED_CPK_FORMATS.")
            }
        }
    }
}

/**
 * Minimum platform version verifier - Checks that none of the CPKs have a minimum platform version that exceeds the current
 * platform version, or that is lower than the minimum platform version at which CPKs were introduced.
 *
 * A value for [CONFIG_ADMIN_PLATFORM_VERSION] needs to be made available through the [ConfigurationAdmin] service.
 */
@Component
internal class MinimumPlatformVersionVerifier @Activate constructor(
        @Reference
        private val configAdmin: ConfigurationAdmin
) : StandaloneCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) {
        val conf = configAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        val platformVersion = conf.properties[CONFIG_ADMIN_PLATFORM_VERSION] as? Int
                ?: throw CpkVerificationException("Parameter platformVersion needs to be set to perform platform " +
                        "verification check.")

        cpks.forEach { cpk ->
            val minPlatformVersion = cpk.cordappManifest.minPlatformVersion

            if (minPlatformVersion > platformVersion) {
                throw CpkVerificationException("CorDapp ${cpk.id} requires minimum platform version " +
                        "$minPlatformVersion, but platform is on $platformVersion.")
            }

            if (minPlatformVersion < DEFAULT_MIN_PLATFORM_VERSION) {
                throw CpkVerificationException("CorDapp ${cpk.id} specifies minimum platform version $minPlatformVersion, which is " +
                        "lower than the required minimum version, $DEFAULT_MIN_PLATFORM_VERSION.")
            }
        }
    }
}

/**
 * CorDapp info verifier - Checks that each CorDapp is either a contract CorDapp, a workflow CorDapp, or both, and
 * specifies a valid version number.
 */
@Component
internal class CordappInfoVerifier : StandaloneCpkVerifier {

    @Suppress("ComplexMethod")
    override fun verify(cpks: Iterable<CPK.Metadata>) = cpks.forEach { cpk ->
        val manifest = cpk.cordappManifest

        if (manifest.contractInfo.shortName == null && manifest.workflowInfo.shortName == null) {
            throw CpkVerificationException("CorDapp ${cpk.id} does not specify whether this CorDapp is a " +
                    "Contract and/or Workflow CorDapp.")
        }

        if (manifest.contractInfo.shortName != null) {
            val version = manifest.contractInfo.versionId ?: throw CpkVerificationException(
                    "CorDapp ${cpk.id} must specify a version in its manifest, which must be a whole number " +
                            "starting from 1.")

            if (version < 1) throw CpkVerificationException(
                    "CorDapp ${cpk.id} specifies version $version. The version must be an integer greater than 0.")
        }

        if (manifest.workflowInfo.shortName != null) {
            val version = manifest.workflowInfo.versionId ?: throw CpkVerificationException(
                    "CorDapp ${cpk.id} must specify a version in its manifest, which must be a whole number " +
                            "starting from 1.")

            if (version < 1) throw CpkVerificationException(
                    "CorDapp ${cpk.id} specifies version $version. The version must be an integer greater than 0.")
        }
    }
}

/**
 * CorDapp signature verifier - Checks that none of the CorDapps have *only* been signed by prohibited signers (i.e.
 * a CorDapp signed by a blacklisted key and a non-blacklisted key is fine).
 *
 * A value for [CONFIG_ADMIN_BLACKLISTED_KEYS] needs to be provided through the [ConfigurationAdmin] service. This
 * list is used to check for CPKs or CorDapps whose signatures are blacklisted.
 */
@Component
internal class CordappSignatureVerifier @Activate constructor(
        @Reference
        private val configAdmin: ConfigurationAdmin,
        @Reference
        private val hashingService: DigestService
) : StandaloneCpkVerifier {

    private fun ByteArray.sha256(): SecureHash = hashingService.hash(this, DigestAlgorithmName.SHA2_256)

    override fun verify(cpks: Iterable<CPK.Metadata>) {
        val conf = configAdmin.getConfiguration(ConfigurationAdmin::class.java.name, null)
        @Suppress("UNCHECKED_CAST") val blacklistedKeys = conf.properties[CONFIG_ADMIN_BLACKLISTED_KEYS] as? List<String>
                ?: throw CpkVerificationException("Parameter blacklistedKeys needs to be set to perform signature verification check.")

        val signerKeyFingerprintBlacklist = blacklistedKeys.map { blacklistedKey ->
            try {
                hashingService.create(blacklistedKey)
            } catch (e: IllegalArgumentException) {
                throw CpkVerificationException("Error while adding key fingerprint $blacklistedKey to blacklistedAttachmentSigningKeys")
            }
        }

        if (signerKeyFingerprintBlacklist.isEmpty()) {
            return
        }


        cpks.forEach { cpk ->
            val certificates = cpk.cordappCertificates
            // We do not check unsigned Cordapps.
            if (certificates.isEmpty()) return

            val nonBlacklistedCertificates = certificates.filterNot { certificate ->
                // Might need the double hashing fixing
                certificate.publicKey.sha256Bytes().sha256() in signerKeyFingerprintBlacklist
            }

            if (nonBlacklistedCertificates.isEmpty()) {
                throw CpkVerificationException("CPK ${cpk.id} is only signed by blacklisted keys (probably the " +
                        "development key).")
            }
        }
    }
}

/**
 * Dependencies met verifier - Checks that the dependencies of the provided group of CPKs are all met by CPKs in this
 * group, and do not require additional CPKs for resolution.
 */
@Component
internal class DependenciesMetVerifier : GroupCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>)  {
        try {
            CPK.resolveDependencies(cpks, true)
        } catch (ex : DependencyResolutionException) {
            throw CpkVerificationException(ex.message ?: "Failure during CPK dependency resolution", ex)
        }
    }
}

/**
 * Duplicate Cordapp name verifier - Checks that no two Cordapps of the provided group of CPKs
 * have the same bundle symbolic name.
 */
@Component
internal class DuplicateCordappNameVerifier : GroupCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) = detectDuplicates(
            cpks,
            "The hash %s corresponds to multiple CorDapps: %s."
    ) { cpk -> setOf(cpk.id.name) }
}

/**
 * Duplicate Cordapp identifier verifier - Checks that no two Cordapps of the provided group of CPKs are identified by
 * the same <symbolic name, version> pair.
 *
 * This check is only applied to CorDapp bundles, and not to library bundles.
 *
 * Even if two CorDapps with identical symbolic names and versions have different public key hashes, they still fail
 * verification, as we need a unique <symbolic name, version> pair to install the CorDapp into the OSGi framework.
 */
@Component
internal class DuplicateCordappIdentifierVerifier : GroupCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) = detectDuplicates(
            cpks,
            "The identifier %s corresponds to multiple CorDapps: %s."
    ) { cpk -> setOf(cpk.id.name to cpk.id.version) }
}

/**
 * Duplicate contracts verifier - Checks that no two Cordapps of the provided group of CPKs contain the same contract.
 */
@Component
internal class DuplicateContractsVerifier : GroupCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) = detectDuplicates(
            cpks,
            "The contract %s is installed by multiple CorDapps: %s."
    ) { cpk -> cpk.cordappManifest.contracts }
}

/**
 * Duplicate flows verifier - Checks that no two Cordapps of the provided group of CPKs contain the same flow.
 */
@Component
internal class DuplicateFlowsVerifier : GroupCpkVerifier {

    override fun verify(cpks: Iterable<CPK.Metadata>) = detectDuplicates(
            cpks,
            "The flow %s is installed by multiple CorDapps: %s."
    ) { cpk -> cpk.cordappManifest.flows }
}

/**
 * Throws [CpkVerificationException] with the provided [errorMessage] if any of the [cpks] correspond to the same
 * value once the [transform] is applied.
 *
 * The [errorMessage] should be a format string taking two arguments: the duplicate value, and the URIs of the
 * offending CPKs.
 */
private fun detectDuplicates(cpks: Iterable<CPK.Metadata>, errorMessage: String, transform: (cpk: CPK.Metadata) -> Iterable<*>) {
    val duplicateCandidates = cpks.flatMap(transform)

    val duplicate = duplicateCandidates
            .groupingBy { it }.eachCount().filter { (_, count) -> count > 1 }.keys
            // We only flag the first duplicated value, rather than trying to report all of them.
            .firstOrNull()
            // We return if there are no duplicates.
            ?: return

    val offendingCpkUris = cpks
            .filter { cpk -> transform(cpk).contains(duplicate) }
            .mapTo(LinkedHashSet()) { cpk -> cpk.id }

    val formattedErrorMessage = errorMessage.format(duplicate, offendingCpkUris)
    throw CpkVerificationException(formattedErrorMessage)
}

/**
 * No split packages verifier - Checks that no two CPKs in a group export the same package.
 */
@Component
internal class NoSplitPackagesVerifier : GroupCpkVerifier {
    override fun verify(cpks: Iterable<CPK.Metadata>) {
        val cpkExports = cpks.associate { cpk ->
            cpk.id to cpk.cordappManifest.exportPackages
        }

        // Check that every package name is associated with
        // exactly one CPK Identifier.
        val packageNames = mutableMapOf<String, CPK.Identifier>()
        cpkExports.entries.forEach { cpk ->
            cpk.value.forEach { packageName ->
                packageNames.put(packageName, cpk.key)?.also { cpkId ->
                    throw CpkVerificationException(
                        "Package '$packageName' exported by CPK ${cpk.key} is already exported by CPK $cpkId")
                }
            }
        }
    }
}

/**
 * Parse the set of exported packages names from the OSGi 'Export-Package' header.
 */
private val CordappManifest.exportPackages: Set<String>
    get() = attributes[Constants.EXPORT_PACKAGE]?.let(OSGiHeader::parseHeader)?.keys ?: emptySet()
