package net.corda.flow.pipeline

import net.corda.flow.state.impl.CheckpointMetadataKeys.STATE_META_CHECKPOINT_TERMINATED_KEY
import net.corda.libs.statemanager.api.Metadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MetaDataUtilsTest {

    @Test
    fun `Null metadata has additional termination meta`() {
        val expectedMeta = Metadata(mapOf(STATE_META_CHECKPOINT_TERMINATED_KEY to true))
        val result = addTerminationKeyToMeta(null)
        assertThat(result).isEqualTo(expectedMeta)
    }

    @Test
    fun `Non null metadata has additional termination meta`() {
        val inputMeta = Metadata(mapOf("foo" to "bar"))
        val expectedMeta = Metadata(mapOf("foo" to "bar", STATE_META_CHECKPOINT_TERMINATED_KEY to true))
        val result = addTerminationKeyToMeta(inputMeta)
        assertThat(result).isEqualTo(expectedMeta)
    }
}