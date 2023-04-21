package net.corda.rest.server.impl.apigen

import net.corda.rest.durablestream.DurableStreamContext
import net.corda.rest.server.impl.rest.resources.impl.TestDuplicateProtocolVersionAPIImpl
import net.corda.rest.server.apigen.test.TestJavaPrimitivesRestResourceImpl
import net.corda.rest.server.impl.apigen.models.EndpointMethod
import net.corda.rest.server.impl.apigen.models.GenericParameterizedType
import net.corda.rest.server.impl.apigen.models.ParameterType
import net.corda.rest.server.impl.apigen.processing.APIStructureRetriever
import net.corda.rest.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.rest.server.impl.rest.resources.impl.TestRestAPIAnnotatedImpl
import net.corda.rest.server.impl.rest.resources.impl.TestRestApiImpl
import net.corda.rest.test.CalendarRestResource
import net.corda.rest.test.CalendarRestResourceImpl
import net.corda.rest.test.TestHealthCheckAPIImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod

internal class APIStructureRetrieverTest {

    @Test
    @Suppress("ComplexMethod")
    fun `structure withSimpleClass shouldSucceed`() {
        val retriever = APIStructureRetriever(listOf(CalendarRestResourceImpl()))

        val resources = retriever.structure

        assertEquals(1, resources.size)

        with(resources.single()) {
            assertEquals(2, endpoints.size)
            assert(endpoints.any { it.path == "getprotocolversion" })
            assert(endpoints.any { it.path == "daysoftheyear" })

            with(endpoints.single { it.path == "daysoftheyear" }) {
                assertEquals("", description)
                assertEquals("daysOfTheYear", title)
                assertEquals("", description)
                assertEquals(EndpointMethod.POST, method)
                assertEquals(2, parameters.size)
                assert(parameters.any { it.classType == DurableStreamContext::class.java })
                assert(parameters.any { it.classType == Int::class.java })
                with(parameters.single { it.classType == Int::class.java }) {
                    assertEquals("year", id)
                    assertEquals("year", name)
                    assertEquals("", description)
                    assertTrue(required)
                    assertNull(default)
                    assertEquals(ParameterType.BODY, type)
                }
                with(responseBody) {
                    assertEquals("", description)
                    assertEquals(FiniteDurableReturnResult::class.java, type)
                    assertEquals(listOf(GenericParameterizedType(CalendarRestResource.CalendarDay::class.java)), parameterizedTypes)
                }
                with(invocationMethod) {
                    assertEquals(CalendarRestResource::daysOfTheYear.javaMethod, method)
                    assertEquals(CalendarRestResourceImpl::class.java, instance::class.java)
                }
            }
        }
    }

    @Test
    fun `structure withNonRESTInterface shouldIgnoreItSuccessfully`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestRestApiImpl()))

        val resources = retriever.structure

        assertEquals(1, resources.size)
    }

    @Test
    fun `structure withNonRESTInterfaceThatIsAnnotated shouldIgnoreItSuccessfully`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestRestAPIAnnotatedImpl()))

        val resources = retriever.structure

        assertEquals(1, resources.size)
    }

    @Test
    fun `structure withDuplicateResourceName shouldThrow`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestHealthCheckAPIImpl()))
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            retriever.structure
        }
    }

    @Test
    fun `structure withDuplicateEndpointPathInResult shouldThrow`() {
        val retriever = APIStructureRetriever(listOf(TestDuplicateProtocolVersionAPIImpl()))
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            retriever.structure
        }
    }

    @Test
    fun `retrieve Java class should succeed`() {
        val retriever = APIStructureRetriever(listOf(TestJavaPrimitivesRestResourceImpl()))
        val resources = retriever.structure
        assertEquals(1, resources.size)
    }
}