package net.corda.httprpc.server.impl.apigen

import net.corda.httprpc.durablestream.DurableStreamContext
import net.corda.httprpc.server.impl.rpcops.impl.TestDuplicateProtocolVersionAPIImpl
import net.corda.httprpc.server.apigen.test.TestJavaPrimitivesRPCopsImpl
import net.corda.httprpc.server.impl.apigen.models.EndpointMethod
import net.corda.httprpc.server.impl.apigen.models.GenericParameterizedType
import net.corda.httprpc.server.impl.apigen.models.ParameterType
import net.corda.httprpc.server.impl.apigen.processing.APIStructureRetriever
import net.corda.httprpc.server.impl.apigen.processing.streams.FiniteDurableReturnResult
import net.corda.httprpc.server.impl.rpcops.CalendarRPCOps
import net.corda.httprpc.server.impl.rpcops.impl.CalendarRPCOpsImpl
import net.corda.httprpc.server.impl.rpcops.impl.TestHealthCheckAPIImpl
import net.corda.httprpc.server.impl.rpcops.impl.TestRPCAPIAnnotatedImpl
import net.corda.httprpc.server.impl.rpcops.impl.TestRPCAPIImpl
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
        val retriever = APIStructureRetriever(listOf(CalendarRPCOpsImpl()))

        val resources = retriever.structure.getOrThrow()

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
                    assertEquals(listOf(GenericParameterizedType(CalendarRPCOps.CalendarDay::class.java)), parameterizedTypes)
                }
                with(invocationMethod) {
                    assertEquals(CalendarRPCOps::daysOfTheYear.javaMethod, method)
                    assertEquals(CalendarRPCOpsImpl::class.java, instance::class.java)
                }
            }
        }
    }

    @Test
    fun `structure withNonHTTPRPCInterface shouldIgnoreItSuccessfully`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestRPCAPIImpl()))

        val resources = retriever.structure.getOrThrow()

        assertEquals(1, resources.size)
    }

    @Test
    fun `structure withNonHTTPRPCInterfaceThatIsAnnotated shouldIgnoreItSuccessfully`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestRPCAPIAnnotatedImpl()))

        val resources = retriever.structure.getOrThrow()

        assertEquals(1, resources.size)
    }

    @Test
    fun `structure withDuplicateResourceName shouldThrow`() {
        val retriever = APIStructureRetriever(listOf(TestHealthCheckAPIImpl(), TestHealthCheckAPIImpl()))
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            retriever.structure.getOrThrow()
        }
    }

    @Test
    fun `structure withDuplicateEndpointPathInResult shouldThrow`() {
        val retriever = APIStructureRetriever(listOf(TestDuplicateProtocolVersionAPIImpl()))
        Assertions.assertThrows(IllegalArgumentException::class.java) {
            retriever.structure.getOrThrow()
        }
    }

    @Test
    @Suppress("ComplexMethod")
    fun `retrieve Java class should succeed`() {
        val retriever = APIStructureRetriever(listOf(TestJavaPrimitivesRPCopsImpl()))
        val resources = retriever.structure.getOrThrow()
        assertEquals(1, resources.size)
    }
}