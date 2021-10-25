package net.corda.securitymanager.internal

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/** Tests of the [DiscoverySecurityManager]. */
class DiscoverySecurityManagerTests {
    private val testBundleDummyLocation = "test_bundle_dummy_location"
    private val otherBundlesDummyLocation = "other_bundles_dummy_location"

    private val bundleUtils = object : BundleUtils() {
        override fun getBundleLocation(klass: Class<*>) = if (klass === DiscoverySecurityManagerTests::class.java) {
            testBundleDummyLocation
        } else {
            otherBundlesDummyLocation
        }
    }

    @Test
    fun `no permissions are denied by default`() {
        DiscoverySecurityManager(setOf(testBundleDummyLocation), bundleUtils)

        assertDoesNotThrow {
            // This permission stands in for all permissions.
            System.getenv()
        }
    }
}