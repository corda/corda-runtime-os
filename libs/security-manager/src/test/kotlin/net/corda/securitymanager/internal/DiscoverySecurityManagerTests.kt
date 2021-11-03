package net.corda.securitymanager.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.osgi.service.permissionadmin.PermissionInfo
import org.slf4j.Logger

/** Tests of the [DiscoverySecurityManager]. */
class DiscoverySecurityManagerTests {
    companion object {
        private const val testBundleDummyLocation = "test_bundle_dummy_location"
        private const val otherBundlesDummyLocation = "other_bundles_dummy_location"
        private val testBundleDummyLocationPrefix = "test_bundle_dummy_location".dropLast(1)

        private const val GET_ENV_TARGET = "getenv."
        private val getEnvPermInfo = PermissionInfo(RuntimePermission::class.java.name, "${GET_ENV_TARGET}*", "")
    }

    // Returns one location for this test class, and another location for all other classes.
    private val bundleUtils = object : BundleUtils() {
        override fun getBundleLocation(klass: Class<*>) = when (klass) {
            DiscoverySecurityManagerTests::class.java -> testBundleDummyLocation
            else -> otherBundlesDummyLocation
        }
    }

    private val loggedEvents = mutableListOf<String>()

    // Adds info-level log messages to the `loggedEvents` list.
    private val logUtils = object : LogUtils() {
        override fun logInfo(logger: Logger, message: String) {
            loggedEvents.add(message)
        }
    }

    @BeforeEach
    fun reset() {
        System.setSecurityManager(null)
        loggedEvents.clear()
    }

    @Test
    fun `no permissions are denied by default`() {
        DiscoverySecurityManager(setOf(testBundleDummyLocationPrefix), bundleUtils, logUtils)

        assertDoesNotThrow {
            // The permission required for this action stands in for all permissions.
            System.getenv()
        }
    }

    @Test
    fun `permissions from bundles whose location matches one of the prefixes are logged`() {
        DiscoverySecurityManager(setOf(testBundleDummyLocationPrefix), bundleUtils, logUtils)

        System.getenv()

        val expectedLoggedEvent = "${this::class.java} requested permission $getEnvPermInfo."
        assertEquals(1, loggedEvents.count { loggedEvent -> loggedEvent == expectedLoggedEvent })
    }

    @Test
    fun `permissions from bundles whose location equals one of the prefixes are logged`() {
        DiscoverySecurityManager(setOf(testBundleDummyLocation), bundleUtils, logUtils)

        System.getenv()

        val expectedLoggedEvent = "${this::class.java} requested permission $getEnvPermInfo."
        assertEquals(1, loggedEvents.count { loggedEvent -> loggedEvent == expectedLoggedEvent })
    }

    @Test
    fun `permissions from bundles whose location does not match one of the prefixes are not logged`() {
        DiscoverySecurityManager(setOf("non_matching_prefix"), bundleUtils, logUtils)

        System.getenv()

        val expectedLoggedEvent = "${this::class.java} requested permission $getEnvPermInfo."
        assertEquals(0, loggedEvents.count { loggedEvent -> loggedEvent == expectedLoggedEvent })
    }

    @Test
    fun `multiple permissions from bundles whose location matches one of the prefixes are logged`() {
        DiscoverySecurityManager(setOf(testBundleDummyLocationPrefix), bundleUtils, logUtils)

        val envVars = setOf("a", "b")
        envVars.forEach { envVar -> System.getenv(envVar) }

        val expectedLoggedEvents = envVars.map { envVar ->
            val permInfo = PermissionInfo(RuntimePermission::class.java.name, "$GET_ENV_TARGET$envVar", "")
            "${this::class.java} requested permission $permInfo."
        }

        assertEquals(2, loggedEvents.count { loggedEvent -> loggedEvent in expectedLoggedEvents })
    }

    @Test
    fun `permissions from bundles whose location matches multiple prefixes are only logged once`() {
        val overlappingPrefix = testBundleDummyLocationPrefix.dropLast(1)
        DiscoverySecurityManager(setOf(overlappingPrefix), bundleUtils, logUtils)

        System.getenv()

        val expectedLoggedEvent = "${this::class.java} requested permission $getEnvPermInfo."
        assertEquals(1, loggedEvents.count { loggedEvent -> loggedEvent == expectedLoggedEvent })
    }
}