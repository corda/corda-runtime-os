package net.corda.membership.lib.impl

import net.corda.layeredpropertymap.testkit.LayeredPropertyMapMocks
import net.corda.membership.lib.EPOCH_KEY
import net.corda.membership.lib.MODIFIED_TIME_KEY
import net.corda.membership.lib.MPV_KEY
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GroupParametersImplTest {
    private companion object {
        const val MPV = "5000"
        const val EPOCH = "5"
    }

    @Test
    fun `creating GroupParameters`() {
        val map = mapOf(
            EPOCH_KEY to EPOCH,
            MPV_KEY to MPV,
            MODIFIED_TIME_KEY to Instant.now().toString(),
        )

        val groupParameters = LayeredPropertyMapMocks.create<GroupParametersImpl>(map)

        with(groupParameters) {
            assertThat(epoch).isEqualTo(EPOCH.toInt())
            assertThat(minimumPlatformVersion).isEqualTo(MPV.toInt())
            assertThat(modifiedTime).isBefore(Instant.now())
        }
    }
}
