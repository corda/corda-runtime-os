package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.CordaAvroDeserializer
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.schema.registry.AvroSchemaRegistry
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer

class CordaKafkaRPCSenderImplTest {

    private lateinit var cordaSenderImpl: CordaKafkaRPCSenderImpl<String, String>

    private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_RPC_SENDER)

    private var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val schemaRegistry: AvroSchemaRegistry = mock<AvroSchemaRegistry>().also {
        whenever(it.serialize(any())).thenReturn(ByteBuffer.wrap("test".encodeToByteArray()))
        whenever(it.getClassType(any())).thenReturn(String::class.java)
    }
    private val deserializer = CordaAvroDeserializer(schemaRegistry, mock(), String::class.java)
    private val serializer = CordaAvroSerializer<String>(schemaRegistry)

    @Test
    fun `test send request finishes exceptionally due to lack of partitions`() {
        doReturn(lifecycleCoordinator).`when`(lifecycleCoordinatorFactory).createCoordinator(any(), any())
        cordaSenderImpl = CordaKafkaRPCSenderImpl(
            config,
            mock(),
            mock(),
            serializer,
            deserializer,
            lifecycleCoordinatorFactory
        )

        val future = cordaSenderImpl.sendRequest("test")
        assertThrows<CordaRPCAPISenderException> { future.getOrThrow() }
    }
}
