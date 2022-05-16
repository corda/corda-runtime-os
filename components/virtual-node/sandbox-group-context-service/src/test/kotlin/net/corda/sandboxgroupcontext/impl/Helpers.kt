package net.corda.sandboxgroupcontext.impl

import net.corda.libs.packaging.Cpk
import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.CpkIdentifier
import net.corda.libs.packaging.core.CpkManifest
import net.corda.libs.packaging.core.CpkMetadata
import net.corda.libs.packaging.core.CpkType
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.ManifestCorDappInfo
import net.corda.sandbox.SandboxCreationService
import net.corda.sandbox.SandboxGroup
import net.corda.v5.crypto.SecureHash
import org.assertj.core.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

object Helpers {
    private fun mockCpkMetadata(mainBundle: String, name: String, version: String): CpkMetadata
    = mockCpkMetadata(mainBundle, emptyList(), name, version)

    private fun mockCpkMetadata(mainBundle: String, dependencies: List<CpkIdentifier>, name:String, version:String): CpkMetadata {
        val contractInfo = ManifestCorDappInfo("", "", 1, "")
        val workflowInfo = ManifestCorDappInfo("", "", 1, "")
        val cordappManifest = CordappManifest(name, version, 1, 1, contractInfo, workflowInfo, mock())
        val hash = SecureHash("ALGO", "1234567890ABCDEF".toByteArray())
        return CpkMetadata(
            mock(),
            CpkManifest(CpkFormatVersion(1, 0)),
            mainBundle,
            emptyList(),
            dependencies,
            cordappManifest,
            CpkType.CORDA_API,
            hash,
            emptySet()
        )
    }

    fun mockTrivialCpk(mainBundle: String, name: String, version: String) = mockCpk(mockCpkMetadata(mainBundle, name, version))

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
