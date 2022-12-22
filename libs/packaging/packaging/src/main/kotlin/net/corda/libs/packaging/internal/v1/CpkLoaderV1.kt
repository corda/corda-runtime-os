package net.corda.libs.packaging.internal.v1

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CpkDocumentReader
import net.corda.libs.packaging.CpkDocumentReader.Companion.SAME_SIGNER_PLACEHOLDER
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY
import net.corda.libs.packaging.PackagingConstants.CPK_LIB_FOLDER
import net.corda.libs.packaging.PackagingConstants.JAR_FILE_EXTENSION
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.libs.packaging.core.exception.LibraryIntegrityException
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.CpkImpl
import net.corda.libs.packaging.internal.CpkLoader
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.signerSummaryHashForRequiredSigners
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.time.Instant
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

internal object CpkLoaderV1 : CpkLoader {
    internal const val CPK_TYPE = "Corda-CPK-Type"

    @Suppress("LongParameterList")
    private class CpkContext(
        val buffer: ByteArray,
        val verifySignature: Boolean,
        var topLevelJars: Int,
        val fileLocationAppender: (String) -> String,
        var temporaryCpkFile: Path?,
        var cpkType: CpkType,
        val cpkDigest: MessageDigest,
        var cordappFileName: String?,
        val cordappDigest: MessageDigest,
        var cordappCertificates: Set<Certificate>?,
        var cordappManifest: CordappManifest?,
        var cpkManifest: CpkManifest?,
        val libraryMap: NavigableMap<String, SecureHash>,
        var libraryConstraints: NavigableMap<String, SecureHash>,
        var cpkDependencies: NavigableSet<CpkIdentifier>,
        val cpkFileName: String?
    ) {
        @Suppress("ThrowsCount")
        fun validate() {
            when {
                topLevelJars == 0 -> throw PackagingException(fileLocationAppender("No CorDapp JAR found"))
                topLevelJars > 1 -> throw PackagingException(fileLocationAppender("$topLevelJars CorDapp JARs found"))
            }
            libraryMap.forEach { (entryName, hash) ->
                val requiredHash = libraryConstraints[entryName] ?: throw PackagingException(
                    fileLocationAppender("$entryName not found in $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY")
                )
                if (hash != requiredHash) {
                    throw LibraryIntegrityException(
                        fileLocationAppender(
                            "Hash of '$entryName' " +
                                    "differs from the content of $CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY"
                        )
                    )
                }
            }
        }

        fun buildCpk(): Cpk {
            val metadata = buildMetadata()
            val finalCpkFile = temporaryCpkFile?.let {
                val destination = it.parent.resolve(metadata.fileChecksum.toHexString())
                Files.move(it, destination, REPLACE_EXISTING)
                destination.toFile()
            } ?: throw IllegalStateException("This should never happen")

            return CpkImpl(
                metadata = metadata,
                jarFile = finalCpkFile,
                verifySignature = verifySignature,
                path = finalCpkFile.toPath(),
                originalFileName = cpkFileName
            )
        }

        fun buildMetadata(): CpkMetadata {
            return CpkMetadata(
                cpkId = CpkIdentifier(
                    cordappManifest!!.bundleSymbolicName,
                    cordappManifest!!.bundleVersion,
                    cordappCertificates!!.signerSummaryHashForRequiredSigners()
                ),
                type = cpkType,
                manifest = cpkManifest!!,
                mainBundle = cordappFileName!!,
                fileChecksum = SecureHash(DigestAlgorithmName.SHA2_256.name, cpkDigest.digest()),
                cordappManifest = cordappManifest!!,
                cordappCertificates = cordappCertificates!!,
                libraries = Collections.unmodifiableList(ArrayList(libraryMap.keys)),
                dependencies = cpkDependencies.toList(),
                timestamp = Instant.now()
            )
        }
    }

    private fun isMainJar(jarEntry: ZipEntry): Boolean {
        return !jarEntry.isDirectory
                && jarEntry.name.let {
            it.indexOf('/') == -1 && it.endsWith(JAR_FILE_EXTENSION)
        }
    }

    private fun isLibJar(jarEntry: ZipEntry): Boolean {
        return !jarEntry.isDirectory
                && jarEntry.name.let {
            it.startsWith(CPK_LIB_FOLDER) && it.indexOf('/') == CPK_LIB_FOLDER.length &&
                    it.indexOf('/', CPK_LIB_FOLDER.length + 1) == -1 && it.endsWith(JAR_FILE_EXTENSION)
        }
    }

    private fun isManifest(zipEntry: ZipEntry) = zipEntry.name == JarFile.MANIFEST_NAME

    private fun consumeStream(inputStream: InputStream, buffer: ByteArray) {
        while (true) {
            if (inputStream.read(buffer) == -1) break
        }
    }

    @Suppress("NestedBlockDepth", "ThrowsCount", "ComplexMethod")
    private fun processMainJar(
        mainJarStream: InputStream,
        cpkEntry: ZipEntry,
        verifySignature: Boolean,
        ctx: CpkContext
    ) {
        ctx.cordappFileName = cpkEntry.name
        val cordappDigestInputStream = DigestInputStream(mainJarStream, ctx.cordappDigest)
        val (manifest, certificates) = JarInputStream(cordappDigestInputStream, verifySignature).let { jar ->
            val signatureCollector = SignatureCollector()
            try {
                while (true) {
                    val jarEntry = jar.nextJarEntry ?: break
                    if (jarEntry.name == CPK_DEPENDENCIES_FILE_ENTRY) {
                        /** We need to do this as [CpkDocumentReader.readDependencies] closes the stream,
                         * while we still need it afterward **/
                        val uncloseableInputStream = UncloseableInputStream(jar)
                        ctx.cpkDependencies =
                            CpkDocumentReader.readDependencies(cpkEntry.name, uncloseableInputStream, ctx.fileLocationAppender)
                    } else if (jarEntry.name == CPK_DEPENDENCY_CONSTRAINTS_FILE_ENTRY) {
                        val uncloseableInputStream = UncloseableInputStream(jar)
                        ctx.libraryConstraints =
                            CpkDocumentReader.readLibraryConstraints(cpkEntry.name, uncloseableInputStream, ctx.fileLocationAppender)
                    } else {
                        consumeStream(jar, ctx.buffer)
                    }
                    try {
                        signatureCollector.addEntry(jarEntry)
                    } catch (e: IOException) {
                        throw PackagingException(
                            ctx.fileLocationAppender("Could not retrieve certificates of CorDapp JAR"),
                            e
                        )
                    }
                    jar.closeEntry()
                }
            } catch (se: SecurityException) {
                throw InvalidSignatureException(
                    ctx.fileLocationAppender("Detected invalid signature on '${ctx.cordappFileName}'"),
                    se
                )
            }
            val manifest = jar.manifest ?: throw CordappManifestException(
                ctx.fileLocationAppender("Missing manifest from cordapp jar '${ctx.cordappFileName}'")
            )
            manifest.mainAttributes.getValue(CPK_TYPE)?.let(CpkType::parse)?.also { ctx.cpkType = it }
            CordappManifest.fromManifest(manifest) to signatureCollector.certificates
        }

        // Replace any "same as me" placeholders with this CPK's actual summary hash.
        val cpkSummaryHash = certificates.signerSummaryHashForRequiredSigners()
        ctx.cpkDependencies = ctx.cpkDependencies.mapTo(TreeSet()) { cpk ->
            if (cpk.signerSummaryHash === SAME_SIGNER_PLACEHOLDER) {
                CpkIdentifier(cpk.name, cpk.version, cpkSummaryHash)
            } else {
                cpk
            }
        }

        consumeStream(cordappDigestInputStream, ctx.buffer)
        ctx.cordappManifest = manifest
        ctx.cordappCertificates = certificates
        ctx.topLevelJars++
    }

    private fun processLibJar(inputStream: InputStream, cpkEntry: ZipEntry, ctx: CpkContext) {
        val digestBytes = try {
            val libraryMessageDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
            consumeStream(DigestInputStream(inputStream, libraryMessageDigest), ctx.buffer)
            libraryMessageDigest.digest()
        } catch (e: IOException) {
            throw PackagingException(
                ctx.fileLocationAppender("Could not calculate hash of library jar '${cpkEntry.name}"),
                e
            )
        }
        ctx.libraryMap[cpkEntry.name] = SecureHash(DigestAlgorithmName.SHA2_256.name, digestBytes)
    }

    /**
     * Parses the cpk metadata optionally extracting the cpk content in the provided destination
     * [cacheDir] if it is present. A [cpkLocation] string can be provided for more meaningful
     * exception messages in case of errors
     */
    @Suppress("ComplexMethod")
    private fun createContext(
        source: InputStream,
        cacheDir: Path?,
        cpkLocation: String?,
        verifySignature: Boolean,
        cpkFileName: String?
    ): CpkContext {
        val ctx = CpkContext(
            buffer = ByteArray(DEFAULT_BUFFER_SIZE),
            verifySignature = verifySignature,
            topLevelJars = 0,
            temporaryCpkFile = cacheDir
                ?.let((Files::createDirectories))
                ?.let { Files.createTempFile(it, null, ".cpk") },
            cpkType = CpkType.UNKNOWN,
            cpkDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name),
            cordappFileName = null,
            cordappDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name),
            cordappCertificates = null,
            cordappManifest = null,
            cpkManifest = null,
            libraryMap = TreeMap(),
            libraryConstraints = Collections.emptyNavigableMap(),
            cpkDependencies = Collections.emptyNavigableSet(),
            fileLocationAppender = if (cpkLocation == null) {
                { msg: String -> msg }
            } else {
                { msg: String -> "$msg in CPK at $cpkLocation" }
            },
            cpkFileName = cpkFileName
        )
        var stream2BeFullyConsumed: InputStream = DigestInputStream(source, ctx.cpkDigest)
        val temporaryCPKFile = ctx.temporaryCpkFile
        if (temporaryCPKFile == null) {
            JarInputStream(stream2BeFullyConsumed, verifySignature)
        } else {
            Files.createDirectories(temporaryCPKFile.parent)
            stream2BeFullyConsumed = TeeInputStream(stream2BeFullyConsumed, Files.newOutputStream(temporaryCPKFile))
            JarInputStream(stream2BeFullyConsumed, verifySignature)
        }.use { jarInputStream ->
            val jarManifest =
                jarInputStream.manifest ?: throw PackagingException(ctx.fileLocationAppender("Invalid file format"))
            ctx.cpkManifest = CpkManifest(FormatVersionReader.readCpkFormatVersion(Manifest(jarManifest)))
            while (true) {
                val cpkEntry = jarInputStream.nextEntry ?: break
                when {
                    isMainJar(cpkEntry) -> {
                        processMainJar(jarInputStream, cpkEntry, verifySignature, ctx)
                    }
                    isLibJar(cpkEntry) -> processLibJar(jarInputStream, cpkEntry, ctx)
                    isManifest(cpkEntry) -> {
                        ctx.cpkManifest = CpkManifest(FormatVersionReader.readCpkFormatVersion(Manifest(jarInputStream)))
                    }
                }
                jarInputStream.closeEntry()
            }
            consumeStream(stream2BeFullyConsumed, ctx.buffer)
        }
        ctx.validate()
        return ctx
    }

    override fun loadCPK(source: ByteArray, cacheDir: Path?, cpkLocation: String?, verifySignature: Boolean, cpkFileName: String?) =
        source.inputStream().use {
            createContext(it, cacheDir, cpkLocation, verifySignature, cpkFileName).buildCpk()
        }

    override fun loadMetadata(source: ByteArray, cpkLocation: String?, verifySignature: Boolean) =
        source.inputStream().use {
            createContext(it, null, cpkLocation, verifySignature, null).buildMetadata()
        }
}
