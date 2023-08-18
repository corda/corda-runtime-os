package net.corda.rpc.server

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.applications.workers.workercommon.WorkerWebServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
import org.mockito.kotlin.verify

class RPCEndpointManagerImplTest {

    private lateinit var rpcEndpointManager: RPCEndpointManagerImpl
    private lateinit var contextMock: Context
    private val server = org.mockito.Mockito.mock(Javalin::class.java)

    private val REQUEST_STRING = "Request"
    private val HANDLED_STRING = "Handled"

    private val javalinServer: WorkerWebServer<Javalin?> = mock {
        on { getServer() } doReturn server
    }

    private val serializer: CordaAvroSerializer<String> = mock {
        on { serialize(HANDLED_STRING) } doReturn (HANDLED_STRING).toByteArray()
    }
    private val deserializer: CordaAvroDeserializer<String> = mock {
        on { deserialize(any()) } doReturn REQUEST_STRING
    }
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<String>(any()) } doReturn serializer
        on { createAvroDeserializer(any(), org.mockito.kotlin.eq(String::class.java)) } doReturn deserializer
    }

    @BeforeEach
    fun setup() {
        rpcEndpointManager = RPCEndpointManagerImpl(cordaAvroSerializationFactory, javalinServer)
        contextMock = org.mockito.Mockito.mock(Context::class.java)
    }

    @Test
    fun `test registerEndpoint`() {

        var handled = false

        val sampleHandler: (String) -> String = { _ ->
            handled = true
            HANDLED_STRING
        }

        val sampleRequestBytes = "Request".toByteArray()

        val handlerCaptor = argumentCaptor<Handler>()

        // Mock the context for the handler
        whenever(contextMock.bodyAsBytes()).thenReturn(sampleRequestBytes)
        whenever(contextMock.result(anyString())).thenReturn(contextMock)

        rpcEndpointManager.registerEndpoint("/test", sampleHandler, String::class.java)

        // Verify that the handler is registered and behaves correctly
        verify(server).post(eq("/test"), handlerCaptor.capture())
        val capturedHandler = handlerCaptor.firstValue

        // Simulate the handler execution
        capturedHandler.handle(contextMock)

        // Verify that the handler's logic was executed as expected
        verify(contextMock).result(HANDLED_STRING.toByteArray())
        assertTrue(handled)
    }
}
