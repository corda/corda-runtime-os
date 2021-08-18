package net.corda.internal.serialization.amqp

import net.corda.classinfo.ClassInfoService
import net.corda.internal.serialization.amqp.testutils.deserializeAndReturnEnvelope
import net.corda.internal.serialization.amqp.testutils.testDefaultFactoryNoEvolution
import net.corda.internal.serialization.amqp.testutils.testSerializationContext
import net.corda.packaging.Cpk
import net.corda.sandbox.CpkClassInfo
import net.corda.sandbox.Sandbox
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.DigestService
import net.corda.v5.crypto.SecureHash
import net.corda.v5.crypto.getZeroHash
import net.corda.v5.crypto.getAllOnesHash
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.serialization.SerializedBytes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.osgi.framework.Version
import java.io.NotSerializableException
import java.util.NavigableSet
import java.util.TreeSet
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@Timeout(value = 30, unit = TimeUnit.SECONDS)
class SerializeAndReturnMetadataTest {

    val factory = testDefaultFactoryNoEvolution()

    private val digestService: DigestService = mock(DigestService::class.java)

    private fun createCpkClassInfo(classBundleName: String = "dummyBundleName",
                                   classBundleVersion: Version = Version(1, 0, 0),
                                   cpkHash: SecureHash = digestService.getZeroHash(DigestAlgorithmName.SHA2_256),
                                   cpkPublicKeyHashes: NavigableSet<SecureHash> = sortedSetOf(
                                       digestService.getAllOnesHash(DigestAlgorithmName.SHA2_256),
                                       digestService.getZeroHash(DigestAlgorithmName.SHA2_256)
                                   ),
                                   cpkDependencies: Set<SecureHash> = setOf()): CpkClassInfo {

        return CpkClassInfo(
                classBundleName = classBundleName,
                classBundleVersion = classBundleVersion,
                cpkHash = cpkHash,
                cpkPublicKeyHashes = cpkPublicKeyHashes,
                cpkDependencies = cpkDependencies
        )
    }

    private fun createCpkIdentifier(
        cordappSymbolicName: String = "dummyBundleName",
        cordappVersion: Version = Version(1, 0, 0),
        cpkPublicKeyHashes: Set<SecureHash> = setOf(
            digestService.getAllOnesHash(DigestAlgorithmName.SHA2_256),
            digestService.getZeroHash(DigestAlgorithmName.SHA2_256)
        )
    ): Cpk.Identifier {

        return Cpk.Identifier(
                cordappSymbolicName,
                cordappVersion.toString(),
                TreeSet(cpkPublicKeyHashes)
        )
    }

    private fun createSandboxGroup(clazz: Class<*>, cpkIdentifier: Cpk.Identifier): SandboxGroup {
        val sandbox = mock(Sandbox::class.java).apply {
            `when`(loadClass(clazz::class.java.name)).thenReturn(clazz)
        }

        return mock(SandboxGroup::class.java).apply {
            `when`(getSandbox(cpkIdentifier)).thenReturn(sandbox)
        }
    }

    private fun createClassInfoService(clazz: Class<*>, cpkClassInfo: CpkClassInfo = createCpkClassInfo()): ClassInfoService {
        val classInfoService = mock(ClassInfoService::class.java)

        return classInfoService.apply {
            `when`(getClassInfo(clazz)).thenReturn(cpkClassInfo)
        }
    }

    @Throws(NotSerializableException::class)
    private fun <T : Any> SerializationOutput.serialize(obj: T): SerializedBytes<T> {
        val bundleName = "bundle-${obj::class.java.name}"
        val klazz = obj::class.java
        return serialize(obj, testSerializationContext
                .withClassInfoService(createClassInfoService(klazz, createCpkClassInfo(bundleName)))
                .withSandboxGroup(createSandboxGroup(klazz, createCpkIdentifier(bundleName)))
        )
    }

    @Test
    fun `returns an empty CPK registry when the ClassInfoService is not installed`() {
        data class A(val a: Int, val b: String)

        val a = A(10, "20")

        val serialised = SerializationOutput(factory).serialize(a, testSerializationContext)
        val obj = DeserializationInput(factory).deserializeAndReturnEnvelope(serialised)

        assertTrue(obj.envelope.metadata.isEmpty())
    }
}