package net.corda.httprpc.server.impl.internal

import net.corda.httprpc.exception.BadRequestException
import net.corda.httprpc.exception.ForbiddenException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.NotAuthenticatedException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.UnexpectedErrorException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpExceptionMapperTest {

    @Test
    fun `map to response BadRequestException with message`() {
        val e = BadRequestException("Invalid id.")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(400, response.status)
        assertEquals("Invalid id.", response.message)
    }

    @Test
    fun `map to response BadRequestException with message and details`() {
        val e = BadRequestException("Invalid id.", mapOf("abc" to "def"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(400, response.status)
        assertEquals("Invalid id.", response.message)
        assertEquals(1, response.details.size)
        assertEquals("def", response.details["abc"])
    }

    @Test
    fun `map to response ForbiddenException`() {
        val e = ForbiddenException()

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(403, response.status)
        assertEquals("User not authorized.", response.message)
    }

    @Test
    fun `map to response ForbiddenException with message`() {
        val e = ForbiddenException("mess")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(403, response.status)
        assertEquals("mess", response.message)
    }

    @Test
    fun `map to response NotAuthenticatedException`() {
        val e = NotAuthenticatedException()

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(401, response.status)
        assertEquals("User authentication failed.", response.message)
    }

    @Test
    fun `map to response NotAuthenticatedException with message`() {
        val e = NotAuthenticatedException("auth failed")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(401, response.status)
        assertEquals("auth failed", response.message)
    }

    @Test
    fun `test UnexpectedErrorException with no parameters response`() {
        val e = UnexpectedErrorException()

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(500, response.status)
        assertEquals("Unexpected internal error occurred.", response.message)
    }

    @Test
    fun `test UnexpectedErrorException with message response`() {
        val e = UnexpectedErrorException("message")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(500, response.status)
        assertEquals("message", response.message)
    }

    @Test
    fun `test UnexpectedErrorException with message and details response`() {
        val e = UnexpectedErrorException("message", mapOf("key" to "value"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(500, response.status)
        assertEquals("message", response.message)
        assertEquals(1, response.details.size)
        assertEquals("value", response.details["key"])
    }

    @Test
    fun `test ResourceNotFoundException response`() {
        val e = ResourceNotFoundException("User", "userlogin123")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(404, response.status)
        assertEquals("User 'userlogin123' not found.", response.message)
    }

    @Test
    fun `test InternalServerException response`() {
        val e = InternalServerException("message", mapOf("detail" to "someinfo"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(500, response.status)
        assertEquals("message", response.message)
        assertEquals(1, response.details.size)
        assertEquals("someinfo", response.details["detail"])
    }
}