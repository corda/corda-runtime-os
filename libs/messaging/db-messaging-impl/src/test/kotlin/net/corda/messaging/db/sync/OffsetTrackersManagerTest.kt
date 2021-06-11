package net.corda.messaging.db.sync

import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.v5.base.util.millis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class OffsetTrackersManagerTest {

    private val topic1 = "test.topic1"
    private val topic2 = "test.topic2"
    private val offsetsPerTopic = mapOf(
        topic1 to 3L,
        topic2 to 5L
    )

    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        `when`(getMaxOffsetPerTopic()).thenReturn(offsetsPerTopic)
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
        assertThat(offsetTrackersManager.maxVisibleOffset(topic1)).isEqualTo(3)
        assertThat(offsetTrackersManager.maxVisibleOffset(topic2)).isEqualTo(5)

        val topic1Offset = offsetTrackersManager.getNextOffset(topic1)
        val topic2Offset = offsetTrackersManager.getNextOffset(topic2)

        offsetTrackersManager.offsetReleased(topic1, topic1Offset)
        offsetTrackersManager.offsetReleased(topic2, topic2Offset)

        assertTrue(offsetTrackersManager.waitForOffset(topic1, topic1Offset, 5.millis))
        assertTrue(offsetTrackersManager.waitForOffset(topic2, topic1Offset, 5.millis))

        assertThat(offsetTrackersManager.maxVisibleOffset(topic1)).isEqualTo(topic1Offset)
        assertThat(offsetTrackersManager.maxVisibleOffset(topic2)).isEqualTo(topic2Offset)
    }

}