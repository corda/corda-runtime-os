package net.corda.messaging.chunking

import com.typesafe.config.ConfigValueFactory
import net.corda.chunking.ChunkBuilderService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializer
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.messaging.api.chunking.MessagingChunkFactory
import net.corda.schema.configuration.MessagingConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MessagingChunkFactoryImplTest {

    private companion object {
        val config = SmartConfigImpl.empty().withValue(MessagingConfig.MAX_ALLOWED_MSG_SIZE, ConfigValueFactory.fromAnyRef(10))
    }

    private val serializer: CordaAvroSerializer<Any> = mock()
    private val keyDeserializer: CordaAvroDeserializer<Any> = mock()
    private val valueDeserializer: CordaAvroDeserializer<Any> = mock()
    private val chunkBuilderService: ChunkBuilderService = mock()
    private val messagingChunkFactory: MessagingChunkFactory = MessagingChunkFactoryImpl(chunkBuilderService)

    @Test
    fun `create consumer chunk service`() {
        val result = messagingChunkFactory.createConsumerChunkService(keyDeserializer, valueDeserializer, {})
        assertThat(result).isNotNull
    }

    @Test
    fun `create producer chunk service`() {
        val result = messagingChunkFactory.createProducerChunkService(config, serializer)
        assertThat(result).isNotNull
    }
}
