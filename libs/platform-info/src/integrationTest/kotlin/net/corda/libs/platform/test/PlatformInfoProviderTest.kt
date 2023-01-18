package net.corda.libs.platform.test

import net.corda.libs.platform.PlatformInfoProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.osgi.test.common.annotation.InjectService

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

    // Note: We aren't testing for local worker platform version or software version here because the
    // bundleContext for integration tests is different to that for a running worker.  We can still mock in the unit
    // test, though.
}