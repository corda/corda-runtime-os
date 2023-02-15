package net.corda.messaging.chunking

import net.corda.chunking.ChunkBuilderService
import net.corda.crypto.cipher.suite.PlatformDigestService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.messaging.api.chunking.MessagingChunkFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class MessagingChunkFactoryImplTest {
    private val keyDeserializer: CordaAvroDeserializer<Any> = mock()
    private val valueDeserializer: CordaAvroDeserializer<Any> = mock()
    private val serializer: CordaAvroSerializer<Any> = mock()
    private val chunkBuilderService: ChunkBuilderService = mock()
    private val cordaAvroFactory: CordaAvroSerializationFactory = mock()
    private val platformDigestService: PlatformDigestService = mock()
    private val messagingChunkFactory: MessagingChunkFactory = MessagingChunkFactoryImpl(chunkBuilderService, cordaAvroFactory, platformDigestService)

    @BeforeEach
    fun setup() {
        whenever(cordaAvroFactory.createAvroDeserializer(any(), any<Class<Any>>())).thenReturn(valueDeserializer)
        whenever(cordaAvroFactory.createAvroSerializer<Any>(any())).thenReturn(serializer)
    }

    @Test
    fun `create consumer chunk deserializer service`() {
        val result = messagingChunkFactory.createConsumerChunkDeserializerService(keyDeserializer, valueDeserializer, {})
        assertThat(result).isNotNull
    }

    @Test
    fun `create chunk deserializer service`() {
        val result = messagingChunkFactory.createConsumerChunkDeserializerService(keyDeserializer, valueDeserializer, {})
        assertThat(result).isNotNull
    }

    @Test
    fun `create chunk serializer service`() {
        val result = messagingChunkFactory.createChunkSerializerService(10000000)
        assertThat(result).isNotNull
    }
}
