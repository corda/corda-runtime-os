package net.corda.sandboxgroupcontext.impl

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.CordappManifest
import net.corda.libs.packaging.ManifestCordappInfo
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.TreeSet

object Helpers {
    fun mockCpkMetadata(mainBundle: String, name: String, version: String): Cpk.Metadata
    = mockCpkMetadata(mainBundle, emptyList(), name, version)

    fun mockCpkMetadata(mainBundle: String, dependencies: List<Cpk.Identifier>, name:String, version:String): Cpk.Metadata {
        val contractInfo = ManifestCordappInfo("", "", 1, "")
        val workflowInfo = ManifestCordappInfo("", "", 1, "")
        val cordappManifest = CordappManifest(name, version, 1, 1, contractInfo, workflowInfo, mock())
        val hash = SecureHash("ALGO", "1234567890ABCDEF".toByteArray())
        return Cpk.Metadata.newInstance(
            mock(),
            mainBundle,
            emptyList(),
            TreeSet(dependencies),
            cordappManifest,
            Cpk.Type.CORDA_API,
            hash,
            emptySet()
        )
    }

    fun mockTrivialCpk(mainBundle: String, name: String, version: String) = mockCpk(mockCpkMetadata(mainBundle, name, version))

    fun mockCpk(metadata: Cpk.Metadata) = mock<Cpk>().also { doReturn(metadata).whenever(it).metadata }

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
