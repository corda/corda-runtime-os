package net.corda.install.internal.persistence

import net.corda.crypto.testkit.CryptoMocks
import net.corda.install.CpkInstallationException
import net.corda.install.internal.verification.TestUtils.createMockConfigurationAdmin
import net.corda.packaging.Cpb
import net.corda.packaging.Cpk
import net.corda.packaging.PackagingException
import net.corda.packaging.internal.UncloseableInputStream
import net.corda.packaging.internal.ZipTweaker
import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.junit.jupiter.api.io.TempDir
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import org.osgi.service.cm.ConfigurationAdmin
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.util.TreeSet
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class CordaPackagePersistenceTests {
    private lateinit var flowsCpkLocation : Path
    private lateinit var workflowCpkLocation : Path
    private lateinit var contractCpkLocation : Path
    private lateinit var testCpbLocation : Path

    private lateinit var configurationAdmin : ConfigurationAdmin
    private lateinit var flowsCpk : Cpk.Expanded
    private lateinit var workflowCpk : Cpk.Expanded
    private lateinit var contractCpk : Cpk.Expanded
    private lateinit var cordaPackagePersistence : CordaPackagePersistence

    private val cryptoMocks = CryptoMocks()
    private val cryptoLibraryFactory = cryptoMocks.factories.cryptoLibrary
    private val hashingService = cryptoLibraryFactory.getDigestService()


    @BeforeEach
    fun setup(@TempDir junitTestDir : Path) {
        flowsCpkLocation = pathOf("test.cpk.flows")
        flowsCpk = cpk(flowsCpkLocation, junitTestDir)
        workflowCpkLocation = pathOf("test.cpk.workflow")
        workflowCpk = cpk(workflowCpkLocation, junitTestDir)
        contractCpkLocation = pathOf("test.cpk.contract")
        contractCpk = cpk(contractCpkLocation, junitTestDir)
        testCpbLocation = junitTestDir.resolve("test.cpb")
        configurationAdmin = createMockConfigurationAdmin(baseDirectory = junitTestDir.resolve("baseDirectory").toString())
        cordaPackagePersistence = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
    }

    private fun pathOf(propertyName: String): Path {
        return Paths.get(System.getProperty(propertyName) ?: fail("Property '$propertyName' is not defined."))
    }

    private fun cpk(location: Path, rootDir: Path): Cpk.Expanded {
        val expansionLocation = rootDir.resolve("expanded-${location.fileName}")
        return Cpk.Expanded.from(Files.newInputStream(location), expansionLocation, location.toString(), true)
    }

    @Test
    fun `can store CPKs and retrieve them`() {
        val loadedCpk = cordaPackagePersistence.putCpk(Files.newInputStream(workflowCpkLocation))
        val cpkById = cordaPackagePersistence.getCpk(workflowCpk.id)
        assertNotNull(cpkById)
        assertEquals(loadedCpk, cpkById)

        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        assertEquals(loadedCpk, cpkRetrievedByHash)
    }

    @Test
    fun `can store CPBs and retrieve their CPKs `() {
        Cpb.assemble(Files.newOutputStream(testCpbLocation), listOf(flowsCpkLocation, workflowCpkLocation, contractCpkLocation))
        val testCpb = cordaPackagePersistence.putCpb(Files.newInputStream(testCpbLocation))
        val retrievedCpb = cordaPackagePersistence.get(testCpb.identifier)
        assertSame(testCpb, retrievedCpb)

        val expectedCpkIds = sequenceOf(flowsCpk, workflowCpk, contractCpk).mapTo(TreeSet(), Cpk::id)
        val cpkIds = retrievedCpb!!.cpks.mapTo(TreeSet(), Cpk::id)
        assertEquals(expectedCpkIds, cpkIds)

        val workflowCpkFromCpb = testCpb.cpks.find { it.id == workflowCpk.id }
        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        assertEquals(workflowCpkFromCpb, cpkRetrievedByHash)

        val cpkRetrievedById = cordaPackagePersistence.getCpk(workflowCpk.id)
        assertNotNull(cpkRetrievedById)
        assertSame(workflowCpkFromCpb, cpkRetrievedById)
    }

    private val cipherSchemeMetadata : CipherSchemeMetadata = cryptoMocks.schemeMetadata

    @Throws(NoSuchAlgorithmException::class)
    fun newSecureRandom(): SecureRandom = cipherSchemeMetadata.secureRandom

    @Throws(NoSuchAlgorithmException::class)
    fun secureRandomBytes(numOfBytes: Int): ByteArray = ByteArray(numOfBytes).apply { newSecureRandom().nextBytes(this) }

    @Test
    fun `returns null for unstored CPBs and CPKs`() {
        val retrievedCpks = cordaPackagePersistence.get(
            Cpb.Identifier(
                hashingService.hash(
                    secureRandomBytes(hashingService.digestLength(DigestAlgorithmName.SHA2_256)), DigestAlgorithmName.SHA2_256
                )
            )
        )
        assertEquals(null, retrievedCpks)

        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        assertEquals(null, cpkRetrievedByHash)

        val cpkRetrievedById = cordaPackagePersistence.getCpk(workflowCpk.id)
        assertNull(cpkRetrievedById)
    }

    @Test
    fun `The CPK file is persisted without altering it`() {
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        cordaPackagePersistence.putCpk(DigestInputStream(Files.newInputStream(workflowCpkLocation), md))
        val cpkPersistence2 = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
        val cpkRetrievedByHash = cpkPersistence2.get(SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest()))
        assertNotNull(cpkRetrievedByHash)
    }

    @Test
    fun `throws if a CPK cannot be stored to disk because the configuration admin's base-directory property isn't set`() {
        val configurationAdmin = createMockConfigurationAdmin(baseDirectory = null)
        val cpkPersistence = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
        assertThrows(CpkInstallationException::class.java) {
            cpkPersistence.putCpk(Files.newInputStream(workflowCpkLocation))
        }
    }

    @Test
    fun `throws if a CPK cannot be persisted because it is not a valid zip file`() {
        assertThrows(PackagingException::class.java) {
            cordaPackagePersistence.putCpk((ByteArrayInputStream(ByteArray(DEFAULT_BUFFER_SIZE))))
        }
    }


    @Test
    fun `returns an empty set of CPKs when reading CPKs from disk if the CPKs directory doesn't exist`() {
        assertNull(cordaPackagePersistence.getCpk(workflowCpk.id))
    }

    @Test
    fun `throws when reading CPKs from disk if a CPK can't be created by the CPK factory`() {
        val modifiedWorkflowCpk = testCpbLocation.resolveSibling( "workflow-without-bundle-symbolic-name.cpk")
        val cordappJarTweaker = object : ZipTweaker() {
            override fun tweakEntry(
                    inputStream: ZipInputStream,
                    outputStream: ZipOutputStream,
                    currentEntry: ZipEntry,
                    buffer: ByteArray): AfterTweakAction {
                return when (currentEntry.name) {
                    JarFile.MANIFEST_NAME -> {
                        val manifest = Manifest().apply { read(UncloseableInputStream(inputStream)) }
                        manifest.mainAttributes.remove(Attributes.Name(BUNDLE_SYMBOLICNAME))
                        val manifestBytes = ByteArrayOutputStream().let {
                            manifest.write(it)
                            it.toByteArray()
                        }
                        writeZipEntry(outputStream, { ByteArrayInputStream(manifestBytes) }, currentEntry.name, buffer)
                        AfterTweakAction.DO_NOTHING
                    }
                    else -> AfterTweakAction.WRITE_ORIGINAL_ENTRY
                }
            }
        }

        object : ZipTweaker() {
            override fun tweakEntry(inputStream: ZipInputStream,
                                    outputStream: ZipOutputStream,
                                    currentEntry: ZipEntry,
                                    buffer : ByteArray) = if(currentEntry.name == workflowCpk.cordappJarFileName) {
                val baos = ByteArrayOutputStream()
                cordappJarTweaker.run(UncloseableInputStream(inputStream), baos)
                writeZipEntry(outputStream, { ByteArrayInputStream(baos.toByteArray()) }, currentEntry.name, buffer, ZipEntry.STORED)
                AfterTweakAction.DO_NOTHING
            } else {
                AfterTweakAction.WRITE_ORIGINAL_ENTRY
            }
        }.run(Files.newInputStream(workflowCpkLocation), Files.newOutputStream(modifiedWorkflowCpk))
        assertThrows(PackagingException::class.java) {
            cordaPackagePersistence.putCpk(Files.newInputStream(modifiedWorkflowCpk))
        }
    }
}
