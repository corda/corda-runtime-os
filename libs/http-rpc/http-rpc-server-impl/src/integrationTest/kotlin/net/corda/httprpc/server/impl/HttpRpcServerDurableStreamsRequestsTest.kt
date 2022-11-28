package net.corda.httprpc.server.impl

import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.utils.compact
import net.corda.httprpc.test.CalendarRPCOpsImpl
import net.corda.httprpc.test.CustomSerializationAPIImpl
import net.corda.httprpc.test.NumberSequencesRPCOpsImpl
import net.corda.httprpc.test.TestHealthCheckAPIImpl
import net.corda.httprpc.test.utils.FakeSecurityManager
import net.corda.httprpc.test.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.test.utils.WebRequest
import net.corda.httprpc.test.utils.multipartDir
import net.corda.utilities.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class HttpRpcServerDurableStreamsRequestsTest : HttpRpcServerTestBase() {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            val httpRpcSettings = HttpRpcSettings(NetworkHostAndPort("localhost",  0),
                context, null, null, HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE, 20000L)
            server = HttpRpcServerImpl(
                listOf(NumberSequencesRPCOpsImpl(), CalendarRPCOpsImpl(), TestHealthCheckAPIImpl(), CustomSerializationAPIImpl()),
                { FakeSecurityManager() } ,
                httpRpcSettings,
                multipartDir,
                true
            ).apply { start() }
            client = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${server.port}/" +
                    "${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")

        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.close()
            }
        }
    }

    @Test
    fun `POST to numberseq_retrieve should return correct values`() {
        val requestBody = """ { 
            "context": {"currentPosition": 1, "maxCount": 1},
            "type": "ODD"
            }"""

        val responseBody = """{
            |"positionedValues":[{"value":5,"position":2}],
            |"remainingElementsCountEstimate":9223372036854775807
            |}""".compact()

        val response = client.call(net.corda.httprpc.tools.HttpVerb.POST, WebRequest<Any>("numberseq/retrieve", requestBody), userName, password)

        assertEquals(HttpStatus.SC_OK, response.responseStatus, response.toString())
        assertEquals(responseBody, response.body)
    }

    @Test
    fun `POST to numberseq_retrieve with updated position should return correct values`() {

        val requestBodyNewPosition = """ { 
            "context": {"currentPosition": 2, "maxCount": 2},
            "type": "ODD"
            }"""

        val responseBodyNewPosition = """{
            |"positionedValues":[{"value":7,"position":3},{"value":9,"position":4}],
            |"remainingElementsCountEstimate":9223372036854775807
            |}""".compact()
        val secondResponse = client.call(net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("numberseq/retrieve", requestBodyNewPosition), userName, password)
        assertEquals(HttpStatus.SC_OK, secondResponse.responseStatus)
        assertEquals(responseBodyNewPosition, secondResponse.body)
    }

    @Test
    fun `POST to calendar_daysoftheyear should return correct values`() {


        val requestBody = """{
            "context": {"currentPosition": 1, "maxCount": 1},
            "year": "2020"}"""
        val responseBody = """{
            |"positionedValues":[{"value":{"dayOfWeek":"FRIDAY","dayOfYear":"2020-01-03"},"position":2}],
            |"remainingElementsCountEstimate":363,
            |"isLastResult":false}""".compact()

        val response = client.call(net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("calendar/daysoftheyear", requestBody), userName, password)

        assertEquals(HttpStatus.SC_OK, response.responseStatus)
        assertEquals(responseBody, response.body)
    }

    @Test
    fun `POST to calendar_daysoftheyear with update position should return correct values`() {

        val requestBodyNewPosition = """{
            "context": {"currentPosition": 2, "maxCount": 2},
            "year": "2020"}"""

        val responseBodyNewPosition = """{
            |"positionedValues":[{"value":{"dayOfWeek":"SATURDAY","dayOfYear":"2020-01-04"},"position":3},{"value":{"dayOfWeek":"SUNDAY","dayOfYear":"2020-01-05"},"position":4}],
            |"remainingElementsCountEstimate":361,
            |"isLastResult":false}""".compact()

        val secondResponse = client.call(net.corda.httprpc.tools.HttpVerb.POST,
            WebRequest<Any>("calendar/daysoftheyear", requestBodyNewPosition), userName, password)
        assertEquals(HttpStatus.SC_OK, secondResponse.responseStatus)
        assertEquals(responseBodyNewPosition, secondResponse.body)
    }
}
