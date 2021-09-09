package net.corda.install.internal.persistence

import net.corda.install.CpkInstallationException
import net.corda.install.internal.CONFIG_ADMIN_BASE_DIRECTORY
import net.corda.install.internal.CPK_DIRECTORY
import net.corda.install.internal.EXTRACTION_DIRECTORY
import net.corda.install.internal.verification.GroupCpkVerifier
import net.corda.install.internal.verification.StandaloneCpkVerifier
import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.v5.crypto.SecureHash
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.AT_LEAST_ONE
import org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

/**
 * An implementation of [CordaPackagePersistence].
 *
 * While the CPKs are currently persisted to disk, they are likely to be persisted to the database in the future.
 */
@Component(service = [CordaPackagePersistence::class])
internal class CordaPackageFileBasedPersistenceImpl @Activate constructor(
        @Reference
        private val configAdmin: ConfigurationAdmin,
        @Reference(cardinality = AT_LEAST_ONE, policyOption = GREEDY)
        private val standaloneVerifiers: List<StandaloneCpkVerifier>,
        @Reference(cardinality = AT_LEAST_ONE, policyOption = GREEDY)
        private val groupVerifiers: List<GroupCpkVerifier>
) : CordaPackagePersistence {

    /** Represents a group of CPKs, keyed in various ways. */
    private data class StoredArchives(
            val cpbsById: ConcurrentHashMap<Cpb.Identifier, Cpb.Expanded> = ConcurrentHashMap(),
            val cpksById: ConcurrentHashMap<Cpk.Identifier, Cpk.Expanded> = ConcurrentHashMap(),
            val cpksByHash: ConcurrentHashMap<SecureHash, Cpk.Expanded> = ConcurrentHashMap())

    // These fields are lazy because they can't be calculated until `configAdmin` has been initialised.
    private val storedArchives: StoredArchives by lazy { readCpksFromDisk() }
    private val cpkDirectory: Path by lazy { getCpkDirectoryInternal() }
    private val expansionDirectory by lazy { getExpansionDirectoryInternal() }

    override fun get(cpbIdentifier: Cpb.Identifier) = storedArchives.cpbsById[cpbIdentifier]

    override fun getCpbIdentifiers() = storedArchives.cpbsById.keys().toList().toSet()

    override fun getCpk(id : Cpk.Identifier) : Cpk.Expanded? =
            storedArchives.cpksById[id]

    override fun get(cpkHash: SecureHash) =
            storedArchives.cpksByHash[cpkHash]

    override fun hasCpk(cpkHash: SecureHash) = storedArchives.cpksByHash.containsKey(cpkHash)

    override fun putCpb(inputStream : InputStream) : Cpb.Expanded {
        val cpb = Cpb.Expanded.from(inputStream,
                expansionLocation = Files.createTempDirectory(expansionDirectory, "cpb"),
                verifySignature = true)

        // The group verifiers are only applied to CPBs, and not standalone CPKs, at installation time. This is
        // because we do not know in what groupings the standalone CPKs will be installed.
        groupVerifiers.forEach { verifier -> verifier.verify(cpb.cpks) }
        standaloneVerifiers.forEach { verifier -> verifier.verify(cpb.cpks) }

        storedArchives.cpbsById[cpb.identifier] = cpb
        for(cpk in cpb.cpks) addCpk(storedArchives.cpksById, storedArchives.cpksByHash, cpk)
        return cpb
    }

    private fun addCpk(
            cpksById: MutableMap<Cpk.Identifier, Cpk.Expanded>,
            cpksByHash: MutableMap<SecureHash, Cpk.Expanded>,
            cpk : Cpk.Expanded) {
        val previous = cpksById.put(cpk.id, cpk)
        if(previous != null) {
            /**
             * Restore the previous entry.
             * It is possible for there to be multiple CPKs with the same id, e.g. they have been recompiled with EC signatures.
             * Their hash will be different, but id the same.
             * In this case we assume the first CPK to be stored is the one we want in the cpksById map. And all of them get stored in
             * the [StoredArchives.cpksByHash] map.
             */
            cpksById[previous.id] = previous
        }
        cpksByHash[cpk.cpkHash] = cpk
    }

    override fun putCpk(inputStream : InputStream)  : Cpk.Expanded {
        val expansionLocation = Files.createTempDirectory(expansionDirectory, "cpk")
        @Suppress("TooGenericExceptionCaught")
        val cpk = try {
             Cpk.Expanded.from(inputStream,
             expansionLocation = expansionLocation,
             verifySignature = true).also {
                 standaloneVerifiers.forEach { verifier -> verifier.verify(listOf(it)) }
             }
        } catch (ex : Exception) {
            Files.walk(expansionLocation).sorted(Comparator.reverseOrder()).forEach(Files::delete)
            throw ex
        }
        val cpkPath = cpkDirectory.resolve(cpk.cpkHash.toHexString() + ".cpk")
        addCpk(storedArchives.cpksById, storedArchives.cpksByHash, cpk)
        Files.copy(cpk.cpkFile, cpkPath)
        return cpk
    }

    /** Returns the [Path] representing the node's CPK directory. */
    private fun getCpkDirectoryInternal(): Path {
        val baseDirectoryString = configAdmin
                .getConfiguration(ConfigurationAdmin::class.java.name, null)
                .properties[CONFIG_ADMIN_BASE_DIRECTORY] as? String
                ?: throw CpkInstallationException("Node's base directory could not be established for storing CPK files.")
        return Paths.get(baseDirectoryString).resolve(CPK_DIRECTORY).also { Files.createDirectories(it) }
    }

    /** Returns the [Path] representing the node's CPK directory. */
    private fun getExpansionDirectoryInternal(): Path {
        val baseDirectoryString = configAdmin
                .getConfiguration(ConfigurationAdmin::class.java.name, null)
                .properties[CONFIG_ADMIN_BASE_DIRECTORY] as? String
                ?: throw CpkInstallationException("Node's base directory could not be established for extracting CPB/CPK files.")
        return Paths.get(baseDirectoryString).resolve(EXTRACTION_DIRECTORY).also {
            Files.createDirectories(it)
            //wipe the expansion directory when the process terminates
            Runtime.getRuntime().addShutdownHook(Thread {
                if(Files.exists(it)) Files.walk(it).sorted(Comparator.reverseOrder()).forEach(Files::delete)
            })
        }
    }

    /**
     * Creates [Cpk]s from the CPK files stored on disk and returns a [StoredCpks] object.
     *
     * Skips verification, as this was already performed when the CPKs were originally installed/fetched.
     *
     * Does not repopulate the map of CPBs. It is assumed for now that all CPBs remain in the node's `cordapps` folder
     * and can be reloaded from there.
     */
    private fun readCpksFromDisk(): StoredArchives {
        // If the CPK directory does not exist, we return early. The directory will be created when the first CPK is
        // written to it.
        if (!Files.exists(cpkDirectory)) return StoredArchives()

        val cpksById = ConcurrentHashMap<Cpk.Identifier, Cpk.Expanded>()
        val cpksByHash = ConcurrentHashMap<SecureHash, Cpk.Expanded>()

        Files.list(cpkDirectory)
                .filter { it.fileName.toString().endsWith(Cpk.fileExtension) }
                .forEach { cpkPath ->
            val cpk = Cpk.Expanded.from(Files.newInputStream(cpkPath),
                    expansionLocation = expansionDirectory.resolve(cpkPath.fileName),
                    cpkLocation = cpkPath.toString(), true)
            addCpk(cpksById, cpksByHash, cpk)
        }
        return StoredArchives(cpksById = cpksById, cpksByHash = cpksByHash)
    }
}

