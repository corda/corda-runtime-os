package net.corda.configuration.rpcops.impl.v1

import net.corda.data.ExceptionEnvelope
import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.virtualnode.rpcops.impl.v1.ExceptionTranslator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/** Tests of [ExceptionTranslator]. */
class ExceptionTranslatorTest {

    @Test
    fun `returns InternalServerException when exception is null`() {
        val exception: ExceptionEnvelope? = null

        val httpApiException = ExceptionTranslator.translate(exception)

        assertEquals(InternalServerException::class.java, httpApiException::class.java)
        assertNotNull(httpApiException.message)
    }

    @Test
    fun `translates IllegalArgumentException to BadRequestException`() {
        val exception = ExceptionEnvelope(IllegalArgumentException::class.java.name, "test")

        val httpApiException = ExceptionTranslator.translate(exception)

        assertEquals(BadRequestException::class.java, httpApiException::class.java)
        assertEquals("test", httpApiException.message)
    }

    @Test
    fun `translates CpiNotFoundException to BadRequestException`() {
        val exception = ExceptionEnvelope(CpiNotFoundException::class.java.name, "test")

        val httpApiException = ExceptionTranslator.translate(exception)

        assertEquals(BadRequestException::class.java, httpApiException::class.java)
        assertEquals("test", httpApiException.message)
    }

    @Test
    fun `translates VirtualNodeAlreadyExistsException to ResourceAlreadyExistsException`() {
        val exception = ExceptionEnvelope(VirtualNodeAlreadyExistsException::class.java.name, "test")

        val httpApiException = ExceptionTranslator.translate(exception)

        assertEquals(ResourceAlreadyExistsException::class.java, httpApiException::class.java)
        assertEquals("test", httpApiException.message)
    }

    @Test
    fun `translates unknown exceptions to InternalServerException`() {
        val exception = ExceptionEnvelope(Exception::class.java.name, "test")

        val httpApiException = ExceptionTranslator.translate(exception)

        assertEquals(InternalServerException::class.java, httpApiException::class.java)
        assertEquals("test", httpApiException.message)
    }
}
