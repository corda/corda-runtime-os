package net.corda.libs.platform.test

import net.corda.libs.platform.PlatformInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.osgi.test.common.annotation.InjectService
import java.util.jar.Manifest

class PlatformInfoProviderTest {

    @InjectService(timeout = 4000)
    lateinit var platformInfoProvider: PlatformInfoProvider

    private companion object {
        const val EXPECTED_STUB_PLATFORM_VERSION = 5000
    }

    @Test
    fun `Service returns expected stub value for active platform version`() {
        val platformVersion = assertDoesNotThrow {
            platformInfoProvider.activePlatformVersion
        }
        assertThat(platformVersion).isEqualTo(EXPECTED_STUB_PLATFORM_VERSION)
    }

    // Note: We aren't testing for local worker platform version here because the bundleContext for integration
    // tests is different to that for a running worker.  We can still mock in the unit test, though.
    //@Test
    //fun `Service returns expected stub value for local worker platform version`() {

    @Test
    fun `Service returns expected value from bundle manifest for local worker software version`() {
        val platformVersion = assertDoesNotThrow {
            platformInfoProvider.localWorkerSoftwareVersion
        }
        val expectedValue = PlatformInfoProvider::class.java.classLoader
            .getResources("META-INF/MANIFEST.MF")
            .asSequence()
            .map {
                it.openStream().use { input ->
                    Manifest(input)
                }
            }.mapNotNull {
                it.mainAttributes
            }.filter {
                it.getValue("Bundle-SymbolicName") == "net.corda.platform-info"
            }.mapNotNull {
                it.getValue("Bundle-Version")
            }.firstOrNull()

        assertThat(expectedValue)
            .isNotNull
            .withFailMessage("Could not read expected software version from bundle manifest.")

        assertThat(platformVersion)
            .isEqualTo(expectedValue)
            .withFailMessage("Platform info service did not return the expected software version.")
    }
}