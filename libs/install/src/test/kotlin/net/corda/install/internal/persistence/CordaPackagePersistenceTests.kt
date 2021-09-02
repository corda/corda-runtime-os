package net.corda.install.internal.persistence

import net.corda.cipher.suite.impl.CipherSchemeMetadataProviderImpl
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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
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
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.jar.Manifest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Disabled("Requires cpks to be built and passed in")
class CordaPackagePersistenceTests {

    private lateinit var testDir : Path

    private lateinit var flowsCpkLocation : Path
    private lateinit var workflowCpkLocation : Path
    private lateinit var contractCpkLocation : Path
    private lateinit var testCpbLocation : Path

    private lateinit var configurationAdmin : ConfigurationAdmin
    private lateinit var flowsCpk : Cpk
    private lateinit var workflowCpk : Cpk
    private lateinit var contractCpk : Cpk
    private lateinit var cordaPackagePersistence : CordaPackagePersistence

    private val cryptoLibraryFactory = CryptoMocks().cryptoLibraryFactory()
    private val hashingService = cryptoLibraryFactory.getDigestService()


    @BeforeEach
    fun setup(@TempDir junitTestDir : Path) {
        testDir = junitTestDir
        flowsCpkLocation = Paths.get(System.getProperty("test.cpk.flows"))
        workflowCpkLocation = Paths.get(System.getProperty("test.cpk.workflow"))
        contractCpkLocation = Paths.get(System.getProperty("test.cpk.contract"))
        testCpbLocation = testDir.resolve("test.cpb")
        configurationAdmin = createMockConfigurationAdmin(baseDirectory = testDir.resolve("baseDirectory").toString())
        flowsCpk = Cpk.Expanded.from(Files.newInputStream(flowsCpkLocation), testDir, flowsCpkLocation.toString(), true)
        workflowCpk = Cpk.Expanded.from(Files.newInputStream(workflowCpkLocation), testDir, workflowCpkLocation.toString(), true)
        contractCpk = Cpk.Expanded.from(Files.newInputStream(contractCpkLocation), testDir, contractCpkLocation.toString(), true)
        cordaPackagePersistence = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
    }

    @Test
    fun `can store CPKs and retrieve them`() {
        val loadedCpk = cordaPackagePersistence.putCpk(Files.newInputStream(workflowCpkLocation))
        val cpkById = cordaPackagePersistence.getCpk(workflowCpk.id)
        Assertions.assertNotNull(cpkById)
        Assertions.assertEquals(loadedCpk, cpkById)

        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        Assertions.assertEquals(loadedCpk, cpkRetrievedByHash)
    }

    @Test
    fun `can store CPBs and retrieve their CPKs `() {
        Cpb.assemble(Files.newOutputStream(testCpbLocation), listOf(flowsCpkLocation, workflowCpkLocation, contractCpkLocation))
        val testCpb = cordaPackagePersistence.putCpb(Files.newInputStream(testCpbLocation))
        val retrievedCpb = cordaPackagePersistence.get(testCpb.identifier)
        Assertions.assertSame(testCpb, retrievedCpb)

        val expectedCpkIds = sequenceOf(flowsCpk, workflowCpk, contractCpk).map {it.id}.toSortedSet()
        val cpkIds = retrievedCpb!!.cpks.asSequence().map {it.id}.toSortedSet()
        Assertions.assertEquals(expectedCpkIds, cpkIds)

        val workflowCpkFromCpb = testCpb.cpks.find { it.id == workflowCpk.id }
        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        Assertions.assertEquals(workflowCpkFromCpb, cpkRetrievedByHash)

        val cpkRetrievedById = cordaPackagePersistence.getCpk(workflowCpk.id)
        Assertions.assertNotNull(cpkRetrievedById)
        Assertions.assertSame(workflowCpkFromCpb, cpkRetrievedById)
    }

    private val cipherSchemeMetadata : CipherSchemeMetadata = CipherSchemeMetadataProviderImpl().getInstance()

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
        Assertions.assertEquals(null, retrievedCpks)

        val cpkRetrievedByHash = cordaPackagePersistence.get(workflowCpk.cpkHash)
        Assertions.assertEquals(null, cpkRetrievedByHash)

        val cpkRetrievedById = cordaPackagePersistence.getCpk(workflowCpk.id)
        Assertions.assertNull(cpkRetrievedById)
    }

    @Test
    fun `The CPK file is persisted without altering it`() {
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        cordaPackagePersistence.putCpk(DigestInputStream(Files.newInputStream(workflowCpkLocation), md))
        val cpkPersistence2 = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
        val cpkRetrievedByHash = cpkPersistence2.get(SecureHash(DigestAlgorithmName.SHA2_256.name, md.digest()))
        Assertions.assertNotNull(cpkRetrievedByHash)
    }

    @Test
    fun `throws if a CPK cannot be stored to disk because the configuration admin's base-directory property isn't set`() {
        val configurationAdmin = createMockConfigurationAdmin(baseDirectory = null)
        val cpkPersistence = CordaPackageFileBasedPersistenceImpl(configurationAdmin, emptyList(), emptyList())
        Assertions.assertThrows(CpkInstallationException::class.java) {
            cpkPersistence.putCpk(Files.newInputStream(workflowCpkLocation))
        }
    }

    @Test
    fun `throws if a CPK cannot be persisted because it is not a valid zip file`() {
        Assertions.assertThrows(PackagingException::class.java) {
            cordaPackagePersistence.putCpk((ByteArrayInputStream(ByteArray(DEFAULT_BUFFER_SIZE))))
        }
    }


    @Test
    fun `returns an empty set of CPKs when reading CPKs from disk if the CPKs directory doesn't exist`() {
        Assertions.assertNull(cordaPackagePersistence.getCpk(workflowCpk.id))
    }

    @Test
    fun `throws when reading CPKs from disk if a CPK can't be created by the CPK factory`() {
        val modifiedWorkflowCpk = testDir.resolve( "workflow-without-bundle-symbolic-name.cpk")
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
        Assertions.assertThrows(PackagingException::class.java) {
            cordaPackagePersistence.putCpk(Files.newInputStream(modifiedWorkflowCpk))
        }
    }
}
