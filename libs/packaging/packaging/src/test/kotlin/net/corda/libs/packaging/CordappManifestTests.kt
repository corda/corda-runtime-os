package net.corda.libs.packaging

import net.corda.libs.packaging.core.CordappManifest
import net.corda.libs.packaging.core.exception.CordappManifestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** Tests of [CordappManifest]. */
class CordappManifestTests {
    @Test
    fun `parses default contract attributes correctly`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest())

        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME, manifest.bundleSymbolicName)
        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_VERSION, manifest.bundleVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_MIN_PLATFORM_VERSION.toInt(), manifest.minPlatformVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_TARGET_PLATFORM_VERSION.toInt(), manifest.targetPlatformVersion)

        assertEquals(TestUtils.MANIFEST_CONTRACT_TYPE, manifest.type)
        assertEquals(TestUtils.MANIFEST_CONTRACT_SHORT_NAME, manifest.shortName)
        assertEquals(TestUtils.MANIFEST_CONTRACT_VENDOR, manifest.vendor)
        assertEquals(TestUtils.MANIFEST_CONTRACT_VERSION_ID, manifest.versionId)
        assertEquals(TestUtils.MANIFEST_CONTRACT_LICENCE, manifest.licence)
    }

    @Test
    fun `parses default workflow attributes correctly`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyWorkflowCordappManifest())

        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_SYMBOLIC_NAME, manifest.bundleSymbolicName)
        assertEquals(TestUtils.MANIFEST_DUMMY_BUNDLE_VERSION, manifest.bundleVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_MIN_PLATFORM_VERSION.toInt(), manifest.minPlatformVersion)
        assertEquals(TestUtils.MANIFEST_DUMMY_TARGET_PLATFORM_VERSION.toInt(), manifest.targetPlatformVersion)

        assertEquals(TestUtils.MANIFEST_WORKFLOW_TYPE, manifest.type)
        assertEquals(TestUtils.MANIFEST_WORKFLOW_SHORT_NAME, manifest.shortName)
        assertEquals(TestUtils.MANIFEST_WORKFLOW_VENDOR, manifest.vendor)
        assertEquals(TestUtils.MANIFEST_WORKFLOW_VERSION_ID, manifest.versionId)
        assertEquals(TestUtils.MANIFEST_WORKFLOW_LICENCE, manifest.licence)
    }

    @Test
    fun `parses other attributes correctly`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest())

        assertEquals(TestUtils.MANIFEST_DUMMY_CONTRACTS.split(',').toSet(), manifest.contracts)
        assertEquals(TestUtils.MANIFEST_DUMMY_FLOWS.split(',').toSet(), manifest.flows)
    }

    @Test
    fun `provides defaults for minimum and target platform version`() {
        val manifest = CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(
                minPlatformVersion = null,
                targetPlatformVersion = null
        ))

        assertEquals(CordappManifest.DEFAULT_MIN_PLATFORM_VERSION, manifest.minPlatformVersion)
        assertEquals(CordappManifest.DEFAULT_MIN_PLATFORM_VERSION, manifest.targetPlatformVersion)
    }

    @Test
    fun `throws if a CorDapp is missing the bundle-symbolicname attribute`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(bundleSymbolicName = null))
        }
    }

    @Test
    fun `throws if a CorDapp is missing the bundle-version attribute`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(bundleVersion = null))
        }
    }

    @Test
    fun `throws if min-platform-version cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(minPlatformVersion = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if target-platform-version cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(targetPlatformVersion = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if a contract CorDapp's version-id attribute cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyContractCordappManifest(contractVersionId = "stringNotInteger"))
        }
    }

    @Test
    fun `throws if a workflow CorDapp's version-id attribute cannot be parsed to an integer`() {
        assertThrows<CordappManifestException> {
            CordappManifest.fromManifest(TestUtils.createDummyWorkflowCordappManifest(workflowVersionId = "stringNotInteger"))
        }
    }
}