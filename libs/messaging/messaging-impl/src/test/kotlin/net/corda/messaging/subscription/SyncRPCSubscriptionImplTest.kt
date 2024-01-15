package net.corda.messaging.subscription

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializer
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.messaging.api.processor.SyncRPCProcessor
import net.corda.messaging.api.subscription.config.SyncRPCConfig
import net.corda.rest.ResponseCode
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncRPCSubscriptionImplTest {

    private val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) }.doReturn(lifecycleCoordinator)
    }

    private val webServer = mock<WebServer>()
    private val subscriptionName = "Test"
    private val endpointPath = "/test"
    private val requestData = "Request String"
    private val serialisedRequest = randomBytes()

    private val responseData = "Response String"
    private val serialisedResponse = randomBytes()

    private val serializer = mock<CordaAvroSerializer<String>> {
        on { serialize(responseData) } doReturn(serialisedResponse)
    }
    private val deserializer = mock<CordaAvroDeserializer<String>> {
        on { deserialize(serialisedRequest) } doReturn(requestData)
    }
    private val context = mock<WebContext> {
        on { bodyAsBytes() } doReturn serialisedRequest
    }
    private val processor = mock<SyncRPCProcessor<String, String>> {
        on { process(requestData) } doReturn (responseData)
    }
    private val rpcSubscriptionConfig = SyncRPCConfig(
        subscriptionName,
        endpointPath
    )

    private val rpcSubscription = SyncRPCSubscriptionImpl(
        rpcSubscriptionConfig, processor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
    )

    private fun randomBytes(): ByteArray {
        return (1..16).map { ('0'..'9').random() }.joinToString("").toByteArray()
    }

    @Test
    fun `when start register endpoint`() {
        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())

        rpcSubscription.start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val endpoint = endpointCaptor.firstValue
        assertSoftly {
            it.assertThat(endpoint.methodType).isEqualTo(HTTPMethod.POST)
            it.assertThat(endpoint.path).isEqualTo(endpointPath)
            it.assertThat(endpoint.webHandler).isNotNull
            it.assertThat(endpoint.isApi).isTrue
        }
    }

    @Test
    fun `registered handler processes deserialised payload and sets results`() {
        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())

        rpcSubscription.start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(deserializer).deserialize(serialisedRequest)
        verify(processor).process(requestData)
        verify(context).result(serialisedResponse)
    }

    @Test
    fun `when null response from the handler then return zero length byte array`() {
        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        whenever(processor.process(requestData)).thenReturn(null)
        rpcSubscription.start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(deserializer).deserialize(serialisedRequest)
        verify(processor).process(requestData)
        verify(context).result(eq(ByteArray(0)))
    }

    @Test
    fun `when request deserialisation fails set result`() {

        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())

        val invalidDeserializer = mock<CordaAvroDeserializer<String>> {
            on { deserialize(serialisedRequest) } doReturn(null)
        }

        SyncRPCSubscriptionImpl(
            rpcSubscriptionConfig, processor, lifecycleCoordinatorFactory, webServer, serializer, invalidDeserializer
        ).start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(context).status(ResponseCode.BAD_REQUEST)
    }

    @Test
    fun `when request process fails set result`() {

        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())

        val ex = Exception("Foobar")
        val invalidProcessor = mock<SyncRPCProcessor<String, String>> {
            on { process(requestData) }  doAnswer { throw ex }
        }

        SyncRPCSubscriptionImpl(
            rpcSubscriptionConfig, invalidProcessor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
        ).start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(context).status(ResponseCode.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `when response cannot be serialised set result`() {

        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())

        val incompleteSerialiser = mock<CordaAvroSerializer<String>> {
            on { serialize(responseData) } doReturn(null)
        }

        SyncRPCSubscriptionImpl(
            rpcSubscriptionConfig, processor, lifecycleCoordinatorFactory, webServer, incompleteSerialiser, deserializer
        ).start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(context).status(ResponseCode.INTERNAL_SERVER_ERROR)
    }

    @Test
    fun `when transient exception is thrown set 503 status`() {
        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        whenever(processor.process(any())).thenThrow(CordaHTTPServerTransientException("transient error"))

        SyncRPCSubscriptionImpl(
            rpcSubscriptionConfig, processor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
        ).start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(context).status(ResponseCode.SERVICE_UNAVAILABLE)
    }

    @Test
    fun `when runtime exception is thrown set 500 status`() {
        val endpointCaptor = argumentCaptor<Endpoint>()
        doNothing().whenever(webServer).registerEndpoint(endpointCaptor.capture())
        whenever(processor.process(any())).thenThrow(CordaRuntimeException("runtime error"))

        SyncRPCSubscriptionImpl(
            rpcSubscriptionConfig, processor, lifecycleCoordinatorFactory, webServer, serializer, deserializer
        ).start()

        assertThat(endpointCaptor.allValues.size).isEqualTo(1)
        val handler = endpointCaptor.firstValue.webHandler

        handler.handle(context)

        verify(context).status(ResponseCode.INTERNAL_SERVER_ERROR)
    }

}