package net.corda.messaging.utils

import net.corda.messaging.api.exception.CordaHTTPServerTransientException
import net.corda.rest.ResponseCode
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.slf4j.Logger
import java.lang.Exception

class SyncRPCUtilsKtTest {

    private val log = mock<Logger>()
    private val endpoint = Endpoint(HTTPMethod.GET, "/path", mock())
    private val webContext = mock<WebContext>()

    @Test
    fun `handleProcessorException handles transient exceptions with service unavailable`() {
        val e = CordaHTTPServerTransientException("req", Exception("inner"))

        handleProcessorException(
            log,
            endpoint,
            e,
            webContext
        )

        val msg = "Transient error processing RPC request for $endpoint: ${e.message}"

        verify(log).warn(msg, e)
        verify(webContext).result(msg)
        verify(webContext).status(ResponseCode.SERVICE_UNAVAILABLE)

    }

    @Test
    fun `handleProcessorException handles other exception types with server error`() {
        val e = CordaRuntimeException("runtime exc", Exception("inner"))

        handleProcessorException(
            log,
            endpoint,
            e,
            webContext
        )

        verify(log).warn("Failed to process RPC request for $endpoint", e)
        verify(webContext).result("Failed to process RPC request for $endpoint")
        verify(webContext).status(ResponseCode.INTERNAL_SERVER_ERROR)
    }
}