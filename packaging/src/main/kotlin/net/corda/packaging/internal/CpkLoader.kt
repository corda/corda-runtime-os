package net.corda.packaging.internal

import net.corda.packaging.CordappManifest
import net.corda.packaging.CordappManifestException
import net.corda.packaging.Cpk
import net.corda.packaging.DependencyMetadataException
import net.corda.packaging.InvalidSignatureException
import net.corda.packaging.PackagingException
import net.corda.v5.base.util.loggerFor
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import org.xml.sax.ErrorHandler
import org.xml.sax.SAXNotRecognizedException
import org.xml.sax.SAXParseException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.cert.Certificate
import java.util.Base64
import java.util.Collections
import java.util.NavigableMap
import java.util.NavigableSet
import java.util.TreeMap
import java.util.TreeSet
import java.util.jar.JarFile
import java.util.jar.JarInputStream
import java.util.jar.Manifest
import java.util.stream.Collectors
import java.util.stream.Stream
import java.util.zip.ZipEntry
import javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD
import javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA
import javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING
import javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.validation.SchemaFactory

object CpkLoader {
    private val logger = loggerFor<CpkLoader>()

    private const val JAR_EXTENSION = ".jar"
    private const val LIB_FOLDER = "lib" // The folder that contains a CPK's library JARs.
    private const val META_INF_FOLDER = "META-INF"

    // The filename of the file specifying a CPK's dependencies.
    private const val DEPENDENCIES_FILE_NAME = "CPKDependencies"
    internal const val DEPENDENCIES_FILE_ENTRY = "$META_INF_FOLDER/$DEPENDENCIES_FILE_NAME"

    // The tags used in the XML of the file specifying a CPK's dependencies.
    private const val DEPENDENCY_TAG = "cpkDependency"
    private const val DEPENDENCY_NAME_TAG = "name"
    private const val DEPENDENCY_VERSION_TAG = "version"
    private const val DEPENDENCY_SIGNERS_TAG = "signers"
    private const val DEPENDENCY_TYPE_TAG = "type"
    private const val DEPENDENCY_SIGNER_TAG = "signer"
    private const val CORDA_CPK_V1 = "corda-cpk-1.0.xsd"

    private fun DocumentBuilderFactory.disableProperty(propertyName: String) {
        try {
            setAttribute(propertyName, "")
        } catch (_: IllegalArgumentException) {
            // Property not supported.
        }
    }

    private fun SchemaFactory.disableProperty(propertyName: String) {
        try {
            setProperty(propertyName, "")
        } catch (_: SAXNotRecognizedException) {
            // Property not supported.
        }
    }

    private val cpkV1DocumentBuilderFactory = DocumentBuilderFactory.newInstance().also { dbf ->
        dbf.setFeature(FEATURE_SECURE_PROCESSING, true)
        dbf.disableProperty(ACCESS_EXTERNAL_SCHEMA)
        dbf.disableProperty(ACCESS_EXTERNAL_DTD)
        dbf.isExpandEntityReferences = false
        dbf.isIgnoringComments = true
        dbf.isNamespaceAware = true

        val cpkSchema = SchemaFactory.newInstance(W3C_XML_SCHEMA_NS_URI).also { sf ->
            sf.setFeature(FEATURE_SECURE_PROCESSING, true)
            sf.disableProperty(ACCESS_EXTERNAL_SCHEMA)
            sf.disableProperty(ACCESS_EXTERNAL_DTD)
        }.newSchema(
            this::class.java.classLoader.getResource(CORDA_CPK_V1) ?: throw IllegalStateException("Corda CPK v1 schema missing")
        )
        dbf.schema = cpkSchema
    }

    @Suppress("LongParameterList")
    private class CpkContext(
            val buffer : ByteArray,
            var topLevelJars : Int,
            val fileLocationAppender: (String) -> String,
            var cpkFile : Path?,
            var cpkType : Cpk.Type,
            val cpkDigest: MessageDigest,
            var cordappFileName: String?,
            val cordappDigest: MessageDigest,
            var cordappCertificates: Set<Certificate>?,
            var cordappManifest: CordappManifest?,
            var cpkManifest: Cpk.Manifest?,
            val libraryMap: NavigableMap<String, SecureHash>,
            var cpkDependencies: NavigableSet<Cpk.Identifier>
    ) {
        fun validate() {
            when {
                topLevelJars == 0 -> throw PackagingException(fileLocationAppender("No CorDapp JAR found"))
                topLevelJars > 1 -> throw PackagingException(fileLocationAppender("$topLevelJars CorDapp JARs found"))
            }
        }

        fun buildExpanded(expansionLocation: Path): Cpk.Expanded {
            val libFolder = expansionLocation.resolve(LIB_FOLDER)
            val libraries = (if (Files.exists(libFolder)) {
                Files.list(libFolder)
            } else {
                Stream.empty()
            }).filter {
                it.fileName.toString().endsWith(JAR_EXTENSION)
            }.collect(Collectors.toUnmodifiableSet())
            return Cpk.Expanded(
                    cpkFile = cpkFile!!,
                    type = cpkType,
                    mainJar = expansionLocation.resolve(cordappFileName),
                    libraries = libraries,
                    cpkManifest = cpkManifest!!,
                    cordappJarFileName = cordappFileName!!,
                    cpkHash = SecureHash(DigestAlgorithmName.SHA2_256.name, cpkDigest.digest()),
                    cordappHash = SecureHash(DigestAlgorithmName.SHA2_256.name, cordappDigest.digest()),
                    cordappCertificates = cordappCertificates!!,
                    cordappManifest = cordappManifest!!,
                    libraryDependencies = Collections.unmodifiableNavigableMap(libraryMap),
                    dependencies = Collections.unmodifiableNavigableSet(cpkDependencies)
            )
        }

        fun buildArchived(): Cpk.Archived {
            return Cpk.Archived(
                    type = cpkType,
                    cpkManifest = cpkManifest!!,
                    cordappJarFileName = cordappFileName!!,
                    cpkHash = SecureHash(DigestAlgorithmName.SHA2_256.name, cpkDigest.digest()),
                    cordappHash = SecureHash(DigestAlgorithmName.SHA2_256.name, cordappDigest.digest()),
                    cordappCertificates = cordappCertificates!!,
                    cordappManifest = cordappManifest!!,
                    libraryDependencies = Collections.unmodifiableNavigableMap(libraryMap),
                    dependencies = Collections.unmodifiableNavigableSet(cpkDependencies)
            )
        }
    }

    private fun isMainJar(jarEntry: ZipEntry): Boolean {
        return !jarEntry.isDirectory
                && jarEntry.name.let {
            it.indexOf('/') == -1 && it.endsWith(JAR_EXTENSION)
        }
    }

    private fun isLibJar(jarEntry: ZipEntry): Boolean {
        return !jarEntry.isDirectory
                && jarEntry.name.let {
            it.startsWith(LIB_FOLDER) && it.indexOf('/') == LIB_FOLDER.length &&
                    it.indexOf('/', LIB_FOLDER.length + 1) == -1 && it.endsWith(JAR_EXTENSION)
        }
    }

    private fun isManifest(zipEntry: ZipEntry) = zipEntry.name == JarFile.MANIFEST_NAME

    private fun consumeStream(inputStream: InputStream, buffer: ByteArray) {
        while (true) {
            if (inputStream.read(buffer) == -1) break
        }
    }

    @Suppress("NestedBlockDepth", "ThrowsCount", "ComplexMethod")
    private fun processMainJar(mainJarStream: InputStream, cpkEntry: ZipEntry, verifySignature: Boolean, ctx: CpkContext) {
        ctx.cordappFileName = cpkEntry.name
        val cordappDigestInputStream = DigestInputStream(mainJarStream, ctx.cordappDigest)
        val (manifest, certificates) = JarInputStream(cordappDigestInputStream, verifySignature).let { jar ->
            val signatureCollector = SignatureCollector()
            try {
                while (true) {
                    val jarEntry = jar.nextJarEntry ?: break
                    if (jarEntry.name == DEPENDENCIES_FILE_ENTRY) {
                        /** We need to do this as [readDependencies] closes the stream, while we still need it afterward **/
                        val uncloseableInputStream = UncloseableInputStream(jar)
                        ctx.cpkDependencies = readDependencies(cpkEntry.name, uncloseableInputStream, ctx.fileLocationAppender)
                    } else {
                        consumeStream(jar, ctx.buffer)
                    }
                    try {
                        signatureCollector.addEntry(jarEntry)
                    } catch (e: IOException) {
                        throw PackagingException(ctx.fileLocationAppender("Could not retrieve certificates of CorDapp JAR"), e)
                    }
                    jar.closeEntry()
                }
            } catch (se: SecurityException) {
                throw InvalidSignatureException(ctx.fileLocationAppender("Detected invalid signature on '${ctx.cordappFileName}'"), se)
            }
            val manifest = jar.manifest ?: throw CordappManifestException(
                    ctx.fileLocationAppender("Missing manifest from cordapp jar '${ctx.cordappFileName}'"))
            manifest.mainAttributes.getValue(Cpk.Manifest.CPK_TYPE)?.let(Cpk.Type::parse)?.also { ctx.cpkType = it }
            CordappManifest.fromManifest(manifest) to signatureCollector.certificates
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
            throw PackagingException(ctx.fileLocationAppender("Could not calculate hash of library jar '${cpkEntry.name}"), e)
        }
        ctx.libraryMap[cpkEntry.name.substring(cpkEntry.name.lastIndexOf('/') + 1)] = SecureHash(DigestAlgorithmName.SHA2_256.name, digestBytes)
    }

    /**
     * Parses the cpk metadata optionally extracting the cpk content in the provided destination
     * [expansionLocation] if it is present. A [cpkLocation] string can be provided for more meaningful
     * exception messages in case of errors
     */
    @Suppress("ComplexMethod")
    fun from(source: InputStream, expansionLocation: Path?, cpkLocation: String?, verifySignature: Boolean): Cpk {
        val ctx = CpkContext(
                buffer = ByteArray(DEFAULT_BUFFER_SIZE),
                topLevelJars = 0,
                cpkFile = expansionLocation?.resolve("source.cpk"),
                cpkType = Cpk.Type.UNKNOWN,
                cpkDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name),
                cordappFileName = null,
                cordappDigest = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name),
                cordappCertificates = null,
                cordappManifest = null,
                cpkManifest = null,
                libraryMap = TreeMap(),
                cpkDependencies = TreeSet(),
                fileLocationAppender = if (cpkLocation == null) {
                    { msg: String -> msg }
                } else {
                    { msg: String -> "$msg in CPK at $cpkLocation" }
                }
        )
        val contentFolder = expansionLocation?.resolve("content")
        var stream2BeFullyConsumed : InputStream = DigestInputStream(source, ctx.cpkDigest)
        if (contentFolder == null) {
            JarInputStream(stream2BeFullyConsumed, verifySignature)
        } else {
            Files.createDirectories(ctx.cpkFile!!.parent)
            stream2BeFullyConsumed = TeeInputStream(stream2BeFullyConsumed, Files.newOutputStream(ctx.cpkFile!!))
            JarExtractorInputStream(stream2BeFullyConsumed, contentFolder, verifySignature, cpkLocation)
        }.use { jarInputStream ->
            val jarManifest = jarInputStream.manifest ?: throw PackagingException(ctx.fileLocationAppender("Invalid file format"))
            ctx.cpkManifest = Cpk.Manifest.fromManifest(Manifest(jarManifest))
            while (true) {
                val cpkEntry = jarInputStream.nextEntry ?: break
                when {
                    isMainJar(cpkEntry) -> {
                        processMainJar(jarInputStream, cpkEntry, verifySignature, ctx)
                    }
                    isLibJar(cpkEntry) -> processLibJar(jarInputStream, cpkEntry, ctx)
                    isManifest(cpkEntry) -> {
                        ctx.cpkManifest = Cpk.Manifest.fromManifest(Manifest(jarInputStream))
                    }
                }
                jarInputStream.closeEntry()
            }
            consumeStream(stream2BeFullyConsumed, ctx.buffer)
        }
        ctx.validate()
        return contentFolder?.let(ctx::buildExpanded) ?: ctx.buildArchived()
    }

    /** Returns the [Cpk.Identifier]s for the provided [dependenciesFileContent]. */
    @Suppress("ThrowsCount", "TooGenericExceptionCaught", "ComplexMethod")
    private fun readDependencies(jarName: String,
                                 dependenciesFileContent: InputStream,
                                 fileLocationAppender : (String) -> String): NavigableSet<Cpk.Identifier> {
        val cpkDependencyDocument: Document = try {
            // The CPK dependencies are specified as an XML file.
            val documentBuilder = cpkV1DocumentBuilderFactory.newDocumentBuilder()
            documentBuilder.setErrorHandler(XmlErrorHandler(jarName))
            documentBuilder.parse(dependenciesFileContent)
        } catch (e: DependencyMetadataException) {
            throw e
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            throw DependencyMetadataException(fileLocationAppender("Error parsing CPK dependencies file"), e)
        }
        val cpkDependencyNodes = cpkDependencyDocument.getElementsByTagName(DEPENDENCY_TAG)

        /**
         * [Iterator] for traversing every [Element] within a [NodeList].
         * Skips any [Node][org.w3c.dom.Node] that is not also an [Element].
         */
        class ElementIterator(private val nodes: NodeList) : Iterator<Element> {
            private var index = 0
            override fun hasNext() = index < nodes.length
            override fun next() = if (hasNext()) nodes.item(index++) as Element else throw NoSuchElementException()
        }
        return ElementIterator(cpkDependencyNodes).asSequence().withIndex().mapNotNull { el ->
            val dependencyNameElements = el.value.getElementsByTagName(DEPENDENCY_NAME_TAG)
            val dependencyVersionElements = el.value.getElementsByTagName(DEPENDENCY_VERSION_TAG)
            val dependencyTypeElements = el.value.getElementsByTagName(DEPENDENCY_TYPE_TAG)
            val dependencySignersElements = el.value.getElementsByTagName(DEPENDENCY_SIGNERS_TAG)

            if (dependencyNameElements.length != 1) {
                // Should already have been validated by schema.
                val msg = fileLocationAppender("The entry at index ${el.index} of the CPK dependencies did not specify its name correctly.")
                throw DependencyMetadataException(msg)
            }
            if (dependencyVersionElements.length != 1) {
                // Should already have been validated by schema.
                val msg = fileLocationAppender("The entry at index ${el.index} of the CPK dependencies file did not specify its version correctly.")
                throw DependencyMetadataException(msg)
            }
            if (dependencySignersElements.length != 1) {
                // Should already have been validated by schema.
                val msg = fileLocationAppender("The entry at index ${el.index} of the CPK dependencies file did not specify its signers correctly.")
                throw DependencyMetadataException(msg)
            }

            val signers = dependencySignersElements.item(0) as Element

            val publicKeySummaryHash: SecureHash = hash { md ->
                ElementIterator(signers.getElementsByTagName(DEPENDENCY_SIGNER_TAG)).asSequence().map { signer ->
                    val algorithm = signer.getAttribute("algorithm")?.trim()
                    if (algorithm.isNullOrEmpty()) {
                        // Should already have been validated by schema.
                        val msg = fileLocationAppender("The entry at index ${el.index} of the CPK dependencies file" +
                                " did not specify an algorithm for its signer's public key hash.")
                        throw DependencyMetadataException(msg)
                    }
                    val hashData = Base64.getDecoder().decode(signer.textContent)
                    SecureHash(algorithm, hashData)
                }.sortedWith(Cpk.Identifier.secureHashComparator)
                .map(SecureHash::toString)
                .map(String::toByteArray)
                .forEach(md::update)
            }
            Cpk.Identifier(
                    dependencyNameElements.item(0).textContent,
                    dependencyVersionElements.item(0).textContent,
                    publicKeySummaryHash).takeIf {
                when(dependencyTypeElements.length) {
                    0 -> true
                    1 -> Cpk.Type.parse(dependencyTypeElements.item(0).textContent) != Cpk.Type.CORDA_API
                    else -> {
                        val msg = fileLocationAppender("The entry at index ${el.index} of the CPK dependencies file has multiple types")
                        throw DependencyMetadataException(msg)
                    }
                }
            }
        }.toCollection(TreeSet())
    }

    private class XmlErrorHandler(private val jarName: String) : ErrorHandler {
        override fun warning(ex: SAXParseException) {
            logger.warn("Problem at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message)
        }

        override fun error(ex: SAXParseException) {
            logger.error("Error at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message)
            throw DependencyMetadataException(ex.message ?: "", ex)
        }

        override fun fatalError(ex: SAXParseException) {
            logger.error("Fatal error at (line {}, column {}) parsing CPK dependencies for {}: {}",
                    ex.lineNumber, ex.columnNumber, jarName, ex.message)
            throw ex
        }
    }
}
