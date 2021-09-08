package net.corda.messaging.db.sync

import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class OffsetTrackersManagerTest {

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"
    private val partition = 1
    private val offsetsPerTopic = mapOf(
        topic1 to mapOf(partition to 3L),
        topic2 to mapOf(partition to 5L)
    )

    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        `when`(getMaxOffsetsPerTopic()).thenReturn(offsetsPerTopic)
    }
    private val offsetTrackersManager = OffsetTrackersManager(dbAccessProvider)

    @BeforeEach
    fun setup() {
        offsetTrackersManager.start()
    }

    @AfterEach
    fun cleanup() {
        offsetTrackersManager.stop()
    }

    @Test
    fun `calls are successfully delegated to separate offset trackers`() {
        assertThat(offsetTrackersManager.maxVisibleOffset(topic1, partition)).isEqualTo(3)
        assertThat(offsetTrackersManager.maxVisibleOffset(topic2, partition)).isEqualTo(5)

        val topic1Offset = offsetTrackersManager.getNextOffset(topic1, partition)
        val topic2Offset = offsetTrackersManager.getNextOffset(topic2, partition)

        offsetTrackersManager.offsetReleased(topic1, partition, topic1Offset)
        offsetTrackersManager.offsetReleased(topic2, partition, topic2Offset)

        offsetTrackersManager.waitForOffsets(topic1, mapOf(partition to topic1Offset), 5.millis)
        offsetTrackersManager.waitForOffsets(topic2, mapOf(partition to topic1Offset), 5.millis)

        assertThat(offsetTrackersManager.maxVisibleOffset(topic1, partition)).isEqualTo(topic1Offset)
        assertThat(offsetTrackersManager.maxVisibleOffset(topic2, partition)).isEqualTo(topic2Offset)
    }

}