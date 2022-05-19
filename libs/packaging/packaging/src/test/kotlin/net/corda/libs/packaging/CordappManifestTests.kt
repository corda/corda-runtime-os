package net.corda.libs.packaging

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.exception.CordappManifestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Tests of [CordappManifest]. */
class CordappManifestTests {
    @Test
    fun `parses default attributes correctly`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyCordappManifest())

        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME, manifest.bundleSymbolicName)
        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_VERSION, manifest.bundleVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_MIN_PLATFORM_VERSION.toInt(), manifest.minPlatformVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_TARGET_PLATFORM_VERSION.toInt(), manifest.targetPlatformVersion)

        assertEquals(TestUtils.MANIFEST_CONTRACT_INFO, manifest.contractInfo)
        assertEquals(TestUtils.MANIFEST_WORKFLOW_INFO, manifest.workflowInfo)
    }

    @Test
    fun `parses other attributes correctly`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyCordappManifest())

        assertEquals(TestUtils.MANIFEST_DUMMY_CONTRACTS.split(',').toSet(), manifest.contracts)
        assertEquals(TestUtils.MANIFEST_DUMMY_FLOWS.split(',').toSet(), manifest.flows)
    }

    @Test
    fun `provides defaults for minimum and target platform version`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(
                minPlatformVersion = null,
                targetPlatformVersion = null
        ))

        assertEquals(CordappManifest.DEFAULT_MIN_PLATFORM_VERSION, manifest.minPlatformVersion)
        assertEquals(CordappManifest.DEFAULT_MIN_PLATFORM_VERSION, manifest.targetPlatformVersion)
    }

    @Test
    fun `throws if a CorDapp is missing the bundle-symbolicname attribute`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(bundleSymbolicName = null))
        }
    }

    @Test
    fun `throws if a CorDapp is missing the bundle-version attribute`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(bundleVersion = null))
        }
    }

    @Test
    fun `throws if min-platform-version cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(minPlatformVersion = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if target-platform-version cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(targetPlatformVersion = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if a contract CorDapp's version-id attribute cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(contractVersionId = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if a workflow CorDapp's version-id attribute cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyCordappManifest(workflowVersionId = "stringNotInteger"))
        }
    }
}