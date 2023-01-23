package net.corda.sandboxgroupcontext.impl

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CordappType
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.Random
import net.corda.v5.crypto.DigestAlgorithmName

object Helpers {
    private fun mockCpkMetadata(
        mainBundle: String,
        name: String,
        version: String,
        fileChecksum: SecureHash = SecureHash("ALGO", "1234567890ABCDEF".toByteArray()),
    ): CpkMetadata {
        val cordappManifest = CordappManifest(name, version, 1, 1,
            CordappType.WORKFLOW, "", "", 0, "", mock())
        return CpkMetadata(
            CpkIdentifier(mainBundle, version, SecureHash.parse("SHA-256:0000000000000000")),
            CpkManifest(CpkFormatVersion(1, 0)),
            mainBundle,
            emptyList(),
            cordappManifest,
            CpkType.CORDA_API,
            fileChecksum,
            emptySet(),
            Instant.now()
        )
    }

    private val random = Random(0)
    private fun newRandomSecureHash(): SecureHash {
        return SecureHash(DigestAlgorithmName.DEFAULT_ALGORITHM_NAME.name, ByteArray(32).also(random::nextBytes))
    }

    fun mockTrivialCpk(mainBundle: String, name: String, version: String, fileChecksum: SecureHash = newRandomSecureHash()) =
        mockCpk(mockCpkMetadata(mainBundle, name, version, fileChecksum))

    private fun mockCpk(metadata: CpkMetadata) = mock<Cpk>().also { doReturn(metadata).whenever(it).metadata }

    /**
     * Create a mock sandbox service that returns a mock sandbox group for each set of cpks.
     *
     * We just use the "main bundle" of each cpk (joined string) to look up the appropriate mock sandbox group to return.
     */
    fun mockSandboxCreationService(cpksPerCpi: List<Set<Cpk>>): SandboxCreationService {
        val mainBundlesToSandboxGroup = mutableMapOf<String, SandboxGroup>()
        for (cpks in cpksPerCpi) {
            val sandboxGroup: SandboxGroup = mock()
            val mainBundles = cpks.map { it.metadata.mainBundle }.toSortedSet().joinToString()
            mainBundlesToSandboxGroup[mainBundles] = sandboxGroup
        }

        val sandboxCreationService: SandboxCreationService = mock()

        Mockito.doAnswer {
            val theirIterator = it.getArgument<Iterable<Cpk>>(0)
            val mainBundles = theirIterator.toList().map { i -> i.metadata.mainBundle }.toSortedSet().joinToString()
            Assertions.assertThat(mainBundlesToSandboxGroup.containsKey(mainBundles)).isTrue // should never fail in the tests...
            mainBundlesToSandboxGroup[mainBundles]
        }.whenever(sandboxCreationService).createSandboxGroup(any(), any())

        return sandboxCreationService
    }
}
