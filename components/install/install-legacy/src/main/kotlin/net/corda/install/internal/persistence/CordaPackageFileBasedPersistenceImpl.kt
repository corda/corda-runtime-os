package net.corda.install.internal.persistence

import net.corda.install.CpkInstallationException
import net.corda.install.internal.CONFIG_ADMIN_BASE_DIRECTORY
import net.corda.install.internal.CPK_DIRECTORY
import net.corda.install.internal.EXTRACTION_DIRECTORY
import net.corda.install.internal.verification.GroupCpkVerifier
import net.corda.install.internal.verification.StandaloneCpkVerifier
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import net.corda.packaging.util.TeeInputStream
import net.corda.utilities.deleteRecursively
import net.corda.v5.base.annotations.VisibleForTesting
import net.corda.v5.crypto.SecureHash
import org.osgi.service.cm.ConfigurationAdmin
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceCardinality.AT_LEAST_ONE
import org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Collections.unmodifiableSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap


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
) : CordaPackagePersistence, AutoCloseable {

    /** Represents a group of CPKs, keyed in various ways. */
    private data class StoredArchives(
        val cpbsById: ConcurrentMap<CPI.Identifier, CPI> = ConcurrentHashMap(),
        val cpksById: ConcurrentMap<CPK.Identifier, CPK> = ConcurrentHashMap(),
        val cpksByHash: ConcurrentMap<SecureHash, CPK> = ConcurrentHashMap())

    // These fields are lazy because they can't be calculated until `configAdmin` has been initialised.
    private val storedArchives: StoredArchives by lazy(::readCpksFromDisk)
    private val cpkDirectory: Path by lazy(::getCpkDirectoryInternal)
    private val expansionDirectory by lazy(::getExpansionDirectoryInternal)

    override fun get(cpbIdentifier: CPI.Identifier) = storedArchives.cpbsById[cpbIdentifier]

    override fun getCpbIdentifiers(): Set<CPI.Identifier> = unmodifiableSet(storedArchives.cpbsById.keys)

    override fun getCpk(id : CPK.Identifier) : CPK? =
            storedArchives.cpksById[id]

    override fun get(cpkHash: SecureHash) =
            storedArchives.cpksByHash[cpkHash]

    override fun hasCpk(cpkHash: SecureHash) = storedArchives.cpksByHash.containsKey(cpkHash)

    override fun putCpb(inputStream : InputStream) : CPI {
        val cpb = CPI.from(inputStream,
                expansionLocation = Files.createTempDirectory(expansionDirectory, "cpb"),
                verifySignature = true)

        // The group verifiers are only applied to CPBs, and not standalone CPKs, at installation time. This is
        // because we do not know in what groupings the standalone CPKs will be installed.
        groupVerifiers.forEach { verifier -> verifier.verify(cpb.cpks.map(CPK::metadata)) }
        standaloneVerifiers.forEach { verifier -> verifier.verify(cpb.cpks.map(CPK::metadata)) }

        storedArchives.cpbsById[cpb.metadata.id] = cpb
        for(cpk in cpb.cpks) addCpk(storedArchives.cpksById, storedArchives.cpksByHash, cpk)
        return cpb
    }

    private fun addCpk(
            cpksById: MutableMap<CPK.Identifier, CPK>,
            cpksByHash: MutableMap<SecureHash, CPK>,
            cpk : CPK) {
        /**
         * Restore the previous entry.
         * It is possible for there to be multiple CPKs with the same id, e.g. they have been recompiled with EC signatures.
         * Their hash will be different, but id the same.
         * In this case we assume the first CPK to be stored is the one we want in the cpksById map. And all of them get stored in
         * the [StoredArchives.cpksByHash] map.
         */
        val res1 = cpksById.putIfAbsent(cpk.metadata.id, cpk)
        val res2 = cpksByHash.putIfAbsent(cpk.metadata.hash, cpk)
        /**
         * If both [res1] and [res2] are not null, that means cpk was not added to any
         * of our maps and needs to be closed to prevent it from being leaked
         */
        if (res1 != null && res2 != null) {
            cpk.close()
        }
    }

    override fun putCpk(inputStream : InputStream) : CPK {
        val tmpFile = Files.createTempFile(cpkDirectory, null, ".cpk")
        val expansionLocation = Files.createTempDirectory(expansionDirectory, "cpk")
        val cpk = Files.newOutputStream(tmpFile).use { destination ->
            TeeInputStream(inputStream, destination).use { teeStream ->
                try {
                    CPK.from(
                        inputStream = teeStream,
                        cacheDir = expansionLocation,
                        verifySignature = true
                    ).also {
                        standaloneVerifiers.forEach { verifier -> verifier.verify(listOf(it.metadata)) }
                    }
                } catch (ex: Exception) {
                    Files.delete(tmpFile)
                    throw ex
                }
            }
        }
        addCpk(storedArchives.cpksById, storedArchives.cpksByHash, cpk)
        try {
            Files.move(tmpFile, tmpFile.parent.resolve(cpk.metadata.hash.toHexString() + ".cpk"), StandardCopyOption.ATOMIC_MOVE)
        } catch (ex : FileAlreadyExistsException) {
            //If a file with the same name already exists, we assume it is exactly the same file as the filename
            // matches the SHA256 hash of its content, hence we just remove the temporary file
            Files.delete(tmpFile)
        }
        return cpk
    }

    @Deactivate
    override fun close() {
        storedArchives.cpbsById.values.forEach(CPI::close)
        storedArchives.cpksById.values.forEach(CPK::close)
        storedArchives.cpksByHash.values.forEach(CPK::close)
        //wipe the expansion directory when the component is deactivated
        expansionDirectory.deleteRecursively()
    }

    @VisibleForTesting
    internal fun deleteCpkDirectory() {
        cpkDirectory.deleteRecursively()
    }

    /** Returns the [Path] representing the node's CPK directory. */
    private fun getCpkDirectoryInternal(): Path {
        val couldNotEstablishBaseDirErr by lazy {
            CpkInstallationException("Node's base directory could not be established for storing CPK files.")
        }

        val properties = configAdmin
            .getConfiguration(ConfigurationAdmin::class.java.name, null)
            .properties ?: throw couldNotEstablishBaseDirErr

        val baseDirectoryString = properties[CONFIG_ADMIN_BASE_DIRECTORY] as? String
            ?: throw couldNotEstablishBaseDirErr

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
        }
    }

    /**
     * Creates [CPK]s from the CPK files stored on disk and returns a [StoredArchives] object.
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

        val cpksById = ConcurrentHashMap<CPK.Identifier, CPK>()
        val cpksByHash = ConcurrentHashMap<SecureHash, CPK>()

        Files.list(cpkDirectory)
                .filter { it.fileName.toString().endsWith(CPK.fileExtension) }
                .forEach { cpkPath ->
            val cpk = CPK.from(Files.newInputStream(cpkPath),
                    cacheDir = expansionDirectory.resolve(cpkPath.fileName),
                    cpkLocation = cpkPath.toString(), true)
            addCpk(cpksById, cpksByHash, cpk)
        }
        return StoredArchives(cpksById = cpksById, cpksByHash = cpksByHash)
    }
}

