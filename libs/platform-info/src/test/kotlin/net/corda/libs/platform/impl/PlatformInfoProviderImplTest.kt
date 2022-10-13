package net.corda.libs.platform.impl

import net.corda.libs.platform.PlatformInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.jar.Manifest

class PlatformInfoProviderImplTest {

    private val platformVersionService = PlatformInfoProviderImpl()

    /**
     * Temporary test until real implementation is added.
     * Stub value and this can be removed once real implementation is available.
     */
    @Test
    fun `active platform version returns stub value`() {
        assertThat(
            platformVersionService.activePlatformVersion
        ).isEqualTo(
            PlatformInfoProviderImpl.STUB_PLATFORM_VERSION
        )
    }

    /**
     * Temporary test until real implementation is added.
     * Stub value and this can be removed once real implementation is available.
     */
    @Test
    fun `local worker platform version returns stub value`() {
        assertThat(
            platformVersionService.localWorkerPlatformVersion
        ).isEqualTo(
            PlatformInfoProviderImpl.STUB_PLATFORM_VERSION
        )
    }

    @Test
    fun `local worker software version returns software version from bundle manifest`() {
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
            .withFailMessage("Failed to get expected software version from bundle for assertion.")

        assertThat(
            platformVersionService.localWorkerSoftwareVersion
        ).isEqualTo(
            expectedValue
        )
    }
}