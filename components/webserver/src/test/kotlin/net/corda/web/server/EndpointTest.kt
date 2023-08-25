package net.corda.web.server

import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.web.api.Endpoint
import net.corda.web.api.HTTPMethod
import net.corda.web.api.WebContext
import net.corda.web.api.WebHandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class EndpointTest {

    private val webHandler = object : WebHandler {
        override fun handle(context: WebContext): WebContext {
            return context
        }
    }

    @Test
    fun `test validate endpoint`() {
        assertThrows<CordaRuntimeException> {
            Endpoint(HTTPMethod.GET, "", webHandler).validate()
        }
        assertThrows<CordaRuntimeException> {
            Endpoint(HTTPMethod.GET, "no-slash", webHandler).validate()
        }
        assertThrows<CordaRuntimeException> {
            Endpoint(HTTPMethod.GET, "not a url", webHandler).validate()
        }
        assertDoesNotThrow {
            Endpoint(HTTPMethod.GET, "/url", webHandler).validate()
        }
    }

}