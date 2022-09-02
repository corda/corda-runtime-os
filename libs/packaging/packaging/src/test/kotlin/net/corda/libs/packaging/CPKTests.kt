package net.corda.libs.packaging

import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.DependencyMetadataException
import net.corda.libs.packaging.core.exception.InvalidSignatureException
import net.corda.libs.packaging.core.exception.LibraryIntegrityException
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.ZipTweaker
import net.corda.libs.packaging.internal.v1.UncloseableInputStream
import net.corda.libs.packaging.internal.v2.CpkLoaderV2
import net.corda.utilities.readAll
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.CopyOption
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

// This is to avoid extracting the CPK archive in every single test case,
// no test case writes anything to the filesystem, nor alters the state of the test class instance;
// this makes it safe to use the same instance for all test cases (test case execution order is irrelevant)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPKTests {

    private lateinit var testDir: Path

    private lateinit var workflowCPKPath: Path
    private lateinit var processedWorkflowCPKPath: Path
    private lateinit var workflowCPK: Cpk
    private lateinit var cordappJarPath: Path
    private lateinit var referenceExtractionPath: Path
    private lateinit var nonJarFile: Path

    private lateinit var workflowCPKLibraries: Map<String, SecureHash>

    private val cordaDevCertSummaryHash = run {
        val certFactory = CertificateFactory.getInstance("X.509")

        val cordaDevCert = certFactory.generateCertificate(
            this::class.java.classLoader.getResourceAsStream("corda_dev_cpk.cer")
                ?: throw IllegalStateException("corda_dev_cpk.cer not found")
        ) as X509Certificate

        val sha256Name = DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name
        SecureHash(
            sha256Name,
            run {
                val md = MessageDigest.getInstance(sha256Name)
                md.update(MemberX500Name.parse(cordaDevCert.subjectX500Principal.name).toString().toByteArray())
                md.digest()
            }
        )
    }

    @BeforeAll
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir

        workflowCPKPath = Path.of(URI(System.getProperty("net.cordapp.packaging.test.workflow.cpk")))
        processedWorkflowCPKPath = testDir.resolve(workflowCPKPath.fileName)
        workflowCPK = Files.newInputStream(workflowCPKPath).use {
            CpkReader.readCpk(it, processedWorkflowCPKPath, workflowCPKPath.toString())
        }
        cordappJarPath = Path.of(URI(System.getProperty("net.cordapp.packaging.test.workflow.cordapp")))
        nonJarFile = Files.createFile(testDir.resolve("someFile.bin"))
        workflowCPKLibraries = System.getProperty("net.cordapp.packaging.test.workflow.libs").split(' ').stream().map { jarFilePath ->
            val filePath = Path.of(URI(jarFilePath))
            Path.of(PackagingConstants.CPK_LIB_FOLDER).resolve(filePath.fileName).toString() to computeSHA256Digest(Files.newInputStream(filePath))
        }.collect(
            Collectors.toUnmodifiableMap({
                // silly hack for Windows - file path uses \ resource path uses /
                it.first.replace('\\', '/')
            }, { it.second })
        )
        workflowCPKLibraries.forEach {
            println("workflowCPKLibraries: ${it.key}|${it.value}")
        }
        referenceExtractionPath = testDir.resolve("unzippedCPK")
        referenceUnzipMethod(workflowCPKPath, referenceExtractionPath)
    }

    companion object {
        private val DUMMY_HASH =
            Base64.getEncoder().encodeToString(SecureHash(DigestAlgorithmName.SHA2_256.name, ByteArray(32)).bytes)

        /** Unpacks the [zip] to an autogenerated temporary directory, and returns the directory's path. */
        fun referenceUnzipMethod(source: Path, destination: Path) {

            // We retrieve the relative paths of the zip entries.
            val zipEntryNames = ZipFile(source.toFile()).use { zipFile ->
                zipFile.entries().toList().map(ZipEntry::getName)
            }

            // We create a filesystem to copy the zip entries to a temporary directory.
            FileSystems.newFileSystem(source, null).use { fs ->
                zipEntryNames
                    .map(fs::getPath)
                    .filterNot(Path::isDirectory)
                    .forEach { path ->
                        val newDir = destination.resolve(path.toString())
                        Files.createDirectories(newDir.parent)
                        path.copyTo(newDir)
                    }
            }
        }

        private fun computeSHA256Digest(stream: InputStream, buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE)): SecureHash {
            val h = hash(DigestAlgorithmName.SHA2_256) { md ->
                var size = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        println("Stream size: $size")
                        break
                    }
                    size += read
                    md.update(buffer, 0, read)
                }
            }
            println("Hash: $h")
            return h
        }
    }

    private fun tweakCordappJar(destination: Path, cordappJarTweaker: ZipTweaker) {
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) = if (currentEntry.name == workflowCPK.metadata.mainBundle) {
                val baos = ByteArrayOutputStream()
                cordappJarTweaker.run(UncloseableInputStream(inputStream), baos)
                writeZipEntry(outputStream, { ByteArrayInputStream(baos.toByteArray()) }, currentEntry.name, buffer, ZipEntry.STORED)
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCPKPath), Files.newOutputStream(destination))
    }

    private fun tweakDependencyMetadataFile(destination: Path, xml: String) {
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY) {
                    val source = {
                        ByteArrayInputStream(xml.toByteArray())
                    }
                    writeZipEntry(outputStream, source, currentEntry.name, buffer, currentEntry.method)
                    AfterTweakAction.DO_NOTHING
                } else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }
        tweakCordappJar(destination, tweaker)
    }

    private fun tamperWithLibraries(destination: Path) {
        object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name.startsWith("lib/")) {
                    val source = {
                        ByteArrayInputStream(ByteArray(0x100))
                    }
                    writeZipEntry(outputStream, source, currentEntry.name, buffer, currentEntry.method)
                    AfterTweakAction.DO_NOTHING
                } else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }.run(Files.newInputStream(workflowCPKPath), Files.newOutputStream(destination))
    }

    @Test
    fun `Verify hashes of jars in the lib folder of workflow cpk`() {
        for (libraryFileName in workflowCPK.metadata.libraries) {
            val libraryHash = workflowCPK.getResourceAsStream(libraryFileName).use(::computeSHA256Digest)
            Assertions.assertEquals(
                workflowCPKLibraries[libraryFileName], libraryHash,
                "The hash of library dependency '$libraryFileName' of cpk file $workflowCPKPath from CPK.Metadata " +
                    "isn't consistent with the content of the file"
            )
        }
    }

    @Test
    fun `Verify hash of cpk file`() {
        val hash = Files.newInputStream(workflowCPKPath).use { computeSHA256Digest(it) }
        Assertions.assertEquals(
            hash, workflowCPK.metadata.fileChecksum,
            "The cpk hash from CPK.Metadata differs from the actual hash of the .cpk file"
        )
    }

    @Test
    fun `Verify library files are correct`() {
        Assertions.assertEquals(workflowCPKLibraries.size, workflowCPK.metadata.libraries.size)
        for (library in workflowCPK.metadata.libraries) {
            val libraryHash = try {
                println("Resource: $library")
                workflowCPK.getResourceAsStream(library).use(::computeSHA256Digest)
            } catch (e: IOException) {
                Assertions.fail(e)
            }
            Assertions.assertEquals(
                libraryHash, workflowCPKLibraries[library],
                "The hash of library dependency '$library' of cpk file $workflowCPKPath from CPK.libraryUris " +
                    "isn't consistent with the content of the file"
            )
        }
    }

    @Test
    fun `Verify CPK dependencies are correct`() {
        val dependencies = workflowCPK.metadata.dependencies
        Assertions.assertEquals(1, dependencies.size)
        val contractCPKDependency = dependencies.single()

        contractCPKDependency.apply {
            Assertions.assertEquals(System.getProperty("net.cordapp.packaging.test.contract.bundle.symbolic.name"), name)
            Assertions.assertEquals(System.getProperty("net.cordapp.packaging.test.contract.bundle.version"), version)
            Assertions.assertEquals(
                cordaDevCertSummaryHash.toString().toByteArray().hash(), signerSummaryHash,
                "The cpk dependency is expected to be signed with corda development key only"
            )
        }
    }

    @Test
    fun `Verify cordapp signature`() {
        Assertions.assertEquals(
            sequenceOf(cordaDevCertSummaryHash).summaryHash(),
            workflowCPK.metadata.cpkId.signerSummaryHash
        )
    }

    @Test
    fun `throws if CorDapp JAR has no manifest`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == JarFile.MANIFEST_NAME) AfterTweakAction.DO_NOTHING
                else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }
        tweakCordappJar(modifiedWorkflowCPK, tweaker)
        assertThrows<CordappManifestException> {
            Files.newInputStream(modifiedWorkflowCPK).use {
                CpkLoaderV2().loadMetadata(it.readAllBytes(),
                    cpkLocation = modifiedWorkflowCPK.toString(),
                    verifySignature = false
                )
            }
        }
    }

    @Test
    fun `throws if a CPK does not have a dependencies file`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY) {
                    val source = {
                        ByteArrayInputStream("<<<<<This is clearly invalid XML content".toByteArray())
                    }
                    writeZipEntry(outputStream, source, currentEntry.name, buffer, currentEntry.method)
                    AfterTweakAction.DO_NOTHING
                } else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }
        tweakCordappJar(modifiedWorkflowCPK, tweaker)
        assertThrows<DependencyMetadataException> {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    fun `does not complain if a CPK dependencies file lists no dependencies`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val xml = """
        |<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        |<cpkDependencies xmlns="urn:corda-cpk">
        |</cpkDependencies>
        """.trimMargin()
        tweakDependencyMetadataFile(modifiedWorkflowCPK, xml)
        Assertions.assertDoesNotThrow {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    fun `throws if a CPK dependencies file lists a dependency with no signers`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val xml = """
        |<cpkDependencies xmlns="urn:corda-cpk">
        |    <cpkDependency>
        |        <name>DUMMY_NAME</name>
        |        <version>DUMMY_VERSION</version>
        |    </cpkDependency>
        |</cpkDependencies>
        """.trimMargin()
        tweakDependencyMetadataFile(modifiedWorkflowCPK, xml)
        assertThrows<DependencyMetadataException> {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    fun `throws if a CPK dependencies file lists a dependency with a signer but no algorithm`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val xml = """
        |<cpkDependencies xmlns="urn:corda-cpk">
        |    <cpkDependency>
        |        <name>DUMMY_NAME</name>
        |        <version>DUMMY_VERSION</version>
        |        <signers>
        |            <signer>$DUMMY_HASH</signer>
        |        </signers>
        |    </cpkDependency>
        |</cpkDependencies>
        """.trimMargin()
        tweakDependencyMetadataFile(modifiedWorkflowCPK, xml)
        assertThrows<DependencyMetadataException> {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    fun `allows a CPK dependencies file to list multiple signers`() {
        val publicKey = "some public key".toByteArray()
        val dummyName = "DUMMY_NAME"
        val dummyVersion = "DUMMY_VERSION"
        val hashdata1 = publicKey.hash(DigestAlgorithmName.SHA2_384)
        val hashdata2 = publicKey.hash(DigestAlgorithmName.SHA2_384)
        val hashdata3 = publicKey.hash(DigestAlgorithmName.SHA2_512)

        val expectedSignersSummaryHash = sequenceOf(hashdata1, hashdata2, hashdata3).summaryHash()

        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val xml = """
        |<cpkDependencies xmlns="urn:corda-cpk">
        |    <cpkDependency>
        |        <name>$dummyName</name>
        |        <version>$dummyVersion</version>
        |        <signers>
        |            <signer algorithm="${hashdata1.algorithm}">${String(Base64.getEncoder().encode(hashdata1.bytes))}</signer>
        |            <signer algorithm="${hashdata2.algorithm}">${String(Base64.getEncoder().encode(hashdata2.bytes))}</signer>
        |            <signer algorithm="${hashdata3.algorithm}">${String(Base64.getEncoder().encode(hashdata3.bytes))}</signer>
        |        </signers>
        |    </cpkDependency>
        |</cpkDependencies>
        """.trimMargin()
        tweakDependencyMetadataFile(modifiedWorkflowCPK, xml)
        val cpk = CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
            cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
        )
        val dependency = cpk.dependencies.find { it.name == dummyName && it.version == dummyVersion }
        Assertions.assertNotNull(dependency, "Test dependency not found")
        Assertions.assertEquals(expectedSignersSummaryHash, dependency!!.signerSummaryHash)
    }

    @Test
    fun `library verification fails if jar file in the lib folder do not match the content of DependencyConstraints`() {
        val tamperedCPK = testDir.resolve("tampered.cpk")
        tamperWithLibraries(tamperedCPK)
        Assertions.assertThrows(LibraryIntegrityException::class.java) {
            CpkLoaderV2().loadMetadata(tamperedCPK.readAll(), null, verifySignature = false)
        }
    }

    @Test
    fun `signature verification fails if archive has been tampered with`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val xml = """
        |<cpkDependencies xmlns="urn:corda-cpk">
        |</cpkDependencies>
        """.trimMargin()
        tweakDependencyMetadataFile(modifiedWorkflowCPK, xml)
        Assertions.assertThrows(InvalidSignatureException::class.java) {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(), null, verifySignature = true)
        }
    }

    @Test
    fun `throws if archive is not a jar file at all`() {
        assertThrows<PackagingException> {
            CpkLoaderV2().loadMetadata(nonJarFile.readAll(),
                nonJarFile.toString(),
                jarSignatureVerificationEnabledByDefault()
            )
        }
        assertThrows<PackagingException> {
            Files.newInputStream(nonJarFile).use { CpkReader.readCpk(it, processedWorkflowCPKPath, nonJarFile.toString()) }
        }
    }

    @Test
    fun `corda-api dependencies are not included in cpk dependencies`() {
        Assertions.assertIterableEquals(listOf("net.cordapp.packaging.test.contract"), workflowCPK.metadata.dependencies.map { it.name })
    }

    @Test
    fun `signers summary hash is computed correctly`() {
        val md = MessageDigest.getInstance(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name)
        md.update(cordaDevCertSummaryHash.toString().toByteArray())
        val expectedHash = SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, md.digest())
        Assertions.assertEquals(expectedHash, workflowCPK.metadata.cpkId.signerSummaryHash)
    }
}

/** @see Files.copy */
fun Path.copyTo(target: Path, vararg options: CopyOption): Path = Files.copy(this, target, *options)

/** @see Files.isDirectory */
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)
