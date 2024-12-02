package net.corda.rest.server.impl.apigen

import net.corda.rest.server.apigen.test.TestJavaPrimitivesRestResourceImpl
import net.corda.rest.server.impl.apigen.processing.APIStructureRetriever
import net.corda.rest.server.impl.rest.resources.impl.TestDuplicateProtocolVersionAPIImpl
import net.corda.rest.server.impl.rest.resources.impl.TestRestAPIAnnotatedImpl
import net.corda.rest.server.impl.rest.resources.impl.TestRestApiImpl
import net.corda.rest.test.TestHealthCheckAPIImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class APIStructureRetrieverTest {

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
