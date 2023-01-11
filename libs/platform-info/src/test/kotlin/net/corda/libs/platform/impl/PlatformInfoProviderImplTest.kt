package net.corda.libs.platform.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.osgi.framework.Bundle
import org.osgi.framework.BundleContext
import org.osgi.framework.Version

class PlatformInfoProviderImplTest {

    companion object {
        const val PLATFORM_VERSION = "999"
        const val SOFTWARE_VERSION = "5.0.0.0-SNAPSHOT"
    }

    private val bundle = mock<Bundle>().also {
        whenever(it.version).thenReturn(Version(SOFTWARE_VERSION))
    }
    private val bundleContext = mock<BundleContext>().also {
        whenever(it.getProperty(eq("net.corda.platform.version"))).thenReturn(PLATFORM_VERSION)
        whenever(it.bundle).thenReturn(bundle)
    }
    private val platformVersionService = PlatformInfoProviderImpl(bundleContext)

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

    @Test
    fun `local worker platform version returns stub value`() {
        assertThat(platformVersionService.localWorkerPlatformVersion).isEqualTo(PLATFORM_VERSION.toInt())
    }

    @Test
    fun `local worker software version returns software version from bundle manifest`() {
        assertThat(platformVersionService.localWorkerSoftwareVersion).isEqualTo(SOFTWARE_VERSION)
    }
}
