package net.corda.messaging.subscription.consumer.listener

import net.corda.messagebus.api.CordaTopicPartition
import net.corda.messagebus.api.consumer.CordaConsumer
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.subscription.listener.ConsumerOffsetProvider
import net.corda.messaging.api.subscription.listener.PartitionAssignmentListener
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class OffsetProviderListenerTest {
    private val clientId = "c1"
    private val partitionAssignmentListener = mock<PartitionAssignmentListener>()
    private val offsetProvider = mock<ConsumerOffsetProvider>().apply {
        whenever(getStartingOffsets(any())).thenReturn(
            mapOf("t" to 2 to 200, "t" to 1 to 100)
        )
    }
    private val consumer = mock<CordaConsumer<*, *>>()
    private val partition1 = CordaTopicPartition("t", 1)
    private val partition2 = CordaTopicPartition("t", 2)
    private val topicPartitions = listOf(partition1, partition2)

    /**
     * Given no partition assignment listener is specified
     * And no offset provider is specified
     * When partitions revoked
     * Then nothing happens
     */
    @Test
    fun `partitions revoked - no lister set - no provider set`() {
        OffsetProviderListener(clientId, null, null, consumer)
            .onPartitionsRevoked(topicPartitions)
    }

    /**
     * Given partition assignment listener is specified
     * And no offset provider is specified
     * When partitions revoked
     * Then nothing happens
     */
    @Test
    fun `partitions revoked - lister set - no provider set`() {
        OffsetProviderListener(clientId, partitionAssignmentListener, null, consumer)
            .onPartitionsRevoked(topicPartitions)
        verify(partitionAssignmentListener).onPartitionsUnassigned(listOf("t" to 1, "t" to 2))
    }

    /**
     * Given partition assignment listener is specified
     * And offset provider is specified
     * When partitions revoked
     * Then nothing happens
     */
    @Test
    fun `partitions revoked - lister set - provider set`() {
        OffsetProviderListener(clientId, partitionAssignmentListener, offsetProvider, consumer)
            .onPartitionsRevoked(topicPartitions)
        verify(partitionAssignmentListener).onPartitionsUnassigned(listOf("t" to 1, "t" to 2))
        verifyNoInteractions(offsetProvider)
    }

    /**
     * Given no partition assignment listener is specified
     * And offset provider is specified
     * When partitions revoked
     * Then nothing happens
     */
    @Test
    fun `partitions revoked - no lister set - provider set`() {
        OffsetProviderListener(clientId, null, offsetProvider, consumer)
            .onPartitionsRevoked(topicPartitions)
        verifyNoInteractions(offsetProvider)
    }

    /**
     * Given no partition assignment listener is specified
     * And no offset provider is specified
     * When partitions revoked
     * Then nothing happens
     */
    @Test
    fun `partitions assigned - no lister set - no provider set`() {
        OffsetProviderListener(clientId, null, null, consumer)
            .onPartitionsAssigned(topicPartitions)
    }

    /**
     * Given partition assignment listener is specified
     * And no offset provider is specified
     * When partitions revoked
     * Then call assigned on listener
     */
    @Test
    fun `partitions assigned - lister set - no provider set`() {
        OffsetProviderListener(clientId, partitionAssignmentListener, null, consumer)
            .onPartitionsAssigned(topicPartitions)
        verify(partitionAssignmentListener).onPartitionsAssigned(listOf("t" to 1, "t" to 2))
    }

    /**
     * Given partition assignment listener is specified
     * And offset provider is specified
     * When partitions revoked
     * Then call assigned on listener
     * And reset offsets to provider offsets
     */
    @Test
    fun `partitions assigned - lister set - provider set`() {
        OffsetProviderListener(clientId, partitionAssignmentListener, offsetProvider, consumer)
            .onPartitionsAssigned(topicPartitions)

        verify(partitionAssignmentListener).onPartitionsAssigned(listOf("t" to 1, "t" to 2))
        verify(consumer).seek(partition1, 100)
        verify(consumer).seek(partition2, 200)
    }

    /**
     * Given no partition assignment listener is specified
     * And offset provider is specified
     * When partitions assigned
     * Then reset offsets to provider offsets
     */
    @Test
    fun `partitions assigned - no lister set - provider set`() {
        OffsetProviderListener(clientId, null, offsetProvider, consumer)
            .onPartitionsAssigned(topicPartitions)
        verifyNoInteractions(partitionAssignmentListener)
        verify(consumer).seek(partition1, 100)
        verify(consumer).seek(partition2, 200)
    }

    /**
     * Given no partition assignment listener is specified
     * And offset provider is specified
     * When partitions assigned
     * And provider fails to return offsets for all partitions
     * Then throw a fatal exception
     */
    @Test
    fun `partitions assigned - no lister set - provider set - invalid offsets`() {
        val partition3 = CordaTopicPartition("t", 3)
        assertThrows<CordaMessageAPIFatalException> {
            OffsetProviderListener(clientId, null, offsetProvider, consumer)
                .onPartitionsAssigned(listOf(partition1, partition2, partition3))
        }
    }
}