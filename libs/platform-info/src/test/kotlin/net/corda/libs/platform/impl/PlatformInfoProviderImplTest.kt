package net.corda.libs.platform.impl

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PlatformInfoProviderImplTest {

    private val platformVersionService = PlatformInfoProviderImpl()

    /**
     * Temporary test until real implementation is added.
     * Stub value and this can be removed once real implementation is available.
     */
    @Test
    fun `platform version service returns stub value`() {
        assertThat(
            platformVersionService.platformVersion
        ).isEqualTo(
            PlatformInfoProviderImpl.STUB_PLATFORM_VERSION
        )
    }
}