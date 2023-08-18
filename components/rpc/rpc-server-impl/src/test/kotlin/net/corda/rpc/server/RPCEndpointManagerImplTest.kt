package net.corda.rpc.server

import io.javalin.http.Context
import io.javalin.http.Handler
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.applications.workers.workercommon.JavalinServer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RPCEndpointManagerImplTest {

    private lateinit var javalinServer: JavalinServer
    private lateinit var rpcEndpointManager: RPCEndpointManagerImpl
    private lateinit var contextMock: Context

    private val REQUEST_STRING = "Request"
    private val HANDLED_STRING = "Handled"


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
        javalinServer = mock(JavalinServer::class.java)
        rpcEndpointManager = RPCEndpointManagerImpl(cordaAvroSerializationFactory, javalinServer)
        contextMock = mock(Context::class.java)
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
        val serverMock = mock(io.javalin.Javalin::class.java)

        whenever(javalinServer.getServer()).thenReturn(serverMock)

        // Mock the context for the handler
        whenever(contextMock.bodyAsBytes()).thenReturn(sampleRequestBytes)
        whenever(contextMock.result(anyString())).thenReturn(contextMock)

        rpcEndpointManager.registerEndpoint("/test", sampleHandler, String::class.java)

        // Verify that the handler is registered and behaves correctly
        verify(serverMock).post(eq("/test"), handlerCaptor.capture())
        val capturedHandler = handlerCaptor.firstValue

        // Simulate the handler execution
        capturedHandler.handle(contextMock)

        // Verify that the handler's logic was executed as expected
        verify(contextMock).result(HANDLED_STRING.toByteArray())
        assertTrue(handled)
    }

}
