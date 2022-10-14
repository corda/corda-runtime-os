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

    @Test
    fun `Service returns expected stub value for local worker platform version`() {
        val platformVersion = assertDoesNotThrow {
            platformInfoProvider.localWorkerPlatformVersion
        }
        assertThat(platformVersion).isEqualTo(EXPECTED_STUB_PLATFORM_VERSION)
    }

    @Test
    fun `Service returns expected value from bundle manifest for local worker software version`() {
        val platformVersion = assertDoesNotThrow {
            platformInfoProvider.localWorkerSoftwareVersion
        }
        val expectedValue = PlatformInfoProvider::class.java.classLoader
            .getResource("META-INF/MANIFEST.MF")
            ?.openStream()
            ?.use {
                Manifest(it)
            }
            ?.mainAttributes
            ?.getValue("Bundle-Version")

        assertThat(expectedValue)
            .isNotNull
            .withFailMessage("Could not read expected software version from bundle manifest.")

        assertThat(platformVersion)
            .isEqualTo(expectedValue)
            .withFailMessage("Platform info service did not return the expected software version.")
    }
}