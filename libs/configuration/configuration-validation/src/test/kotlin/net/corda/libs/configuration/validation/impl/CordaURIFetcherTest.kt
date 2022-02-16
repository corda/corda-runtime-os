package net.corda.libs.configuration.validation.impl

import net.corda.libs.configuration.validation.ConfigurationSchemaFetchException
import net.corda.schema.configuration.provider.ConfigSchemaException
import net.corda.schema.configuration.provider.SchemaProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.InputStream
import java.net.URI

class CordaURIFetcherTest {

    companion object {
        private const val TEST_PATH = "TEST_PATH"
    }

    @Test
    fun `Uses the schema provider to fetch data and removes prefix correctly`() {
        val schemaProvider = mock<SchemaProvider>()
        whenever(schemaProvider.getSchemaFile(TEST_PATH)).thenReturn(TestInputStream)
        val fetcher = CordaURIFetcher(schemaProvider)
        val stream = fetcher.fetch(URI("https://corda.r3.com/$TEST_PATH"))
        assertEquals(TestInputStream, stream)
    }

    @Test
    fun `Throws on null input`() {
        val schemaProvider = mock<SchemaProvider>()
        val fetcher = CordaURIFetcher(schemaProvider)
        assertThrows<ConfigurationSchemaFetchException> {
            fetcher.fetch(null)
        }
    }

    @Test
    fun `Fails when URI has incorrect prefix`() {
        val schemaProvider = mock<SchemaProvider>()
        whenever(schemaProvider.getSchemaFile(TEST_PATH)).thenAnswer { TestInputStream }
        whenever(schemaProvider.getSchemaFile(any())).thenThrow(ConfigSchemaException("foo"))
        val fetcher = CordaURIFetcher(schemaProvider)
        assertThrows<ConfigurationSchemaFetchException> {
            fetcher.fetch(URI("https://example.com/$TEST_PATH"))
        }
    }

    object TestInputStream : InputStream() {
        override fun read(): Int {
            throw IllegalArgumentException()
        }
    }
}