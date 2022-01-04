package net.corda.messaging.kafka.publisher

import com.typesafe.config.Config
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.messaging.kafka.properties.ConfigProperties
import net.corda.messaging.kafka.subscription.net.corda.messaging.kafka.createStandardTestConfig
import net.corda.v5.base.concurrent.getOrThrow
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class CordaKafkaRPCSenderImplTest {

    private lateinit var cordaSenderImpl: CordaKafkaRPCSenderImpl<String, String>

    private val config: Config = createStandardTestConfig().getConfig(ConfigProperties.PATTERN_RPC_SENDER)

    private var lifecycleCoordinatorFactory: LifecycleCoordinatorFactory = mock()
    private var lifecycleCoordinator: LifecycleCoordinator = mock()
    private val deserializer: CordaAvroDeserializer<String> = mock()
    private val serializer: CordaAvroSerializer<String> = mock()

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
