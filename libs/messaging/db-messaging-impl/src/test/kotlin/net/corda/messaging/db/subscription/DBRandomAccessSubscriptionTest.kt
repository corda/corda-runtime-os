package net.corda.messaging.db.subscription

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaMessageAPIFatalException
import net.corda.messaging.api.exception.CordaMessageAPIIntermittentException
import net.corda.messaging.api.subscription.factory.config.SubscriptionConfig
import net.corda.messaging.db.persistence.DBAccessProvider
import net.corda.messaging.db.persistence.RecordDbEntry
import net.corda.messaging.db.sync.OffsetTrackersManager
import net.corda.schema.registry.AvroSchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.sql.SQLNonTransientException
import java.sql.SQLTransientException

class DBRandomAccessSubscriptionTest {

    private val topic = "test.topic"

    private val dbRecords = listOf(
        RecordDbEntry(topic, 1, 1, "key-1".toByteArray(), "value-1".toByteArray()),
        RecordDbEntry(topic, 1, 2, "key-2".toByteArray(), "value-2".toByteArray()),
        RecordDbEntry(topic, 1, 3, "key-3".toByteArray(), "value-3".toByteArray())
    )

    private val dbAccessProvider = mock(DBAccessProvider::class.java).apply {
        `when`(getRecord(anyString(), anyInt(), anyLong())).thenAnswer { invocation ->
            val topic = invocation.arguments[0] as String
            val partition = invocation.arguments[1] as Int
            val offset = invocation.arguments[2] as Long
            dbRecords.find { it.topic == topic && it.partition == partition && it.offset == offset }
        }
    }

    private val offsetTrackersManager = mock(OffsetTrackersManager::class.java).apply {
        `when`(maxVisibleOffset(anyString(), anyInt())).thenAnswer { invocation ->
            val partition = invocation.arguments[1] as Int
            dbRecords.last { it.partition == partition }.offset
        }
    }

    private val avroSchemaRegistry = mock(AvroSchemaRegistry::class.java).apply {
        `when`(serialize(anyOrNull())).thenAnswer { invocation ->
            val bytes = (invocation.arguments.first() as String).toByteArray()
            ByteBuffer.wrap(bytes)
        }
        `when`(deserialize(anyOrNull(), anyOrNull(), anyOrNull())).thenAnswer { invocation ->
            val bytes = invocation.arguments.first() as ByteBuffer
            StandardCharsets.UTF_8.decode(bytes).toString()
        }
    }

    private val lifecycleCoordinator: LifecycleCoordinator = mock()
    private val lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock {
        on { createCoordinator(any(), any()) } doReturn lifecycleCoordinator
    }

    private val subscriptionConfig = SubscriptionConfig("group-1", topic)
    private val randomAccessSubscription =
        DBRandomAccessSubscription(
            subscriptionConfig,
            avroSchemaRegistry,
            offsetTrackersManager,
            dbAccessProvider,
            lifecycleCoordinatorFactory,
            String::class.java,
            String::class.java
        )

    @Test
    fun `when no record exists at the specified location, null is returned`() {
        val record = randomAccessSubscription.getRecord(1, 10)

        assertThat(record).isNull()
    }

    @Test
    fun `when a record exists at the specified location, it is returned`() {
        val record = randomAccessSubscription.getRecord(1, 2)

        assertThat(record).isNotNull
        assertThat(record!!.topic).isEqualTo(topic)
        assertThat(record.key).isEqualTo("key-2")
        assertThat(record.value).isEqualTo("value-2")
    }

    @Test
    fun `when recoverable exception is thrown, a transient exception is thrown to the client`() {
        `when`(
            dbAccessProvider.getRecord(
                anyString(),
                anyInt(),
                anyLong()
            )
        ).thenAnswer { throw SQLTransientException() }

        assertThatThrownBy { randomAccessSubscription.getRecord(1, 2) }
            .isInstanceOf(CordaMessageAPIIntermittentException::class.java)
    }

    @Test
    fun `when non-recoverable exception is thrown, a fatal exception is thrown to the client`() {
        `when`(
            dbAccessProvider.getRecord(
                anyString(),
                anyInt(),
                anyLong()
            )
        ).thenAnswer { throw SQLNonTransientException() }

        assertThatThrownBy { randomAccessSubscription.getRecord(1, 2) }
            .isInstanceOf(CordaMessageAPIFatalException::class.java)
    }
}
