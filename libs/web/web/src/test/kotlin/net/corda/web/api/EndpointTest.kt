package net.corda.web.api

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EndpointTest {

    private val webHandler = WebHandler { context -> context }

    @ParameterizedTest
    @ValueSource(strings = ["", "noslash", "/not a url"])
    fun `registering an endpoint with improper endpoint string throws`(path: String) {
        assertThrows<IllegalArgumentException> {
            Endpoint(HTTPMethod.GET, path, webHandler)
        }
    }

    @Test
    fun `registering an endpoint with improper endpoint string does not throw`() {
        assertDoesNotThrow {
            Endpoint(HTTPMethod.GET, "/url", webHandler)
        }
    }
}