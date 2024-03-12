package net.corda.rest.server.impl.internal

import net.corda.rest.exception.BadRequestException
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.ForbiddenException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.InvalidStateChangeException
import net.corda.rest.exception.NotAuthenticatedException
import net.corda.rest.exception.OperationNotAllowedException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HttpExceptionMapperTest {

    @Test
    fun `map to response BadRequestException with title`() {
        val e = BadRequestException("Invalid id.")

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(400, response.status)
        assertEquals("Invalid id.", response.message)
    }

    @Test
    fun `map to response BadRequestException with title, details and exceptionDetails`() {
        val e = BadRequestException("Invalid id.", mapOf("abc" to "def"), ExceptionDetails("BadRequestException","Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(400, response.status)
        assertEquals("Invalid id.", response.message)
        assertEquals(3, response.details.size)
        assertEquals("def", response.details["abc"])
        assertEquals("BadRequestException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `map to response ForbiddenException`() {
        val e = ForbiddenException()

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(403, response.status)
        assertEquals("User not authorized.", response.message)
    }

    @Test
    fun `map to response ForbiddenException with title and exceptionDetails`() {
        val e = ForbiddenException("mess", exceptionDetails = ExceptionDetails("ForbiddenException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(403, response.status)
        assertEquals("mess", response.message)
        assertEquals("ForbiddenException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `map to response NotAuthenticatedException`() {
        val e = NotAuthenticatedException()

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(401, response.status)
        assertEquals("User authentication failed.", response.message)
    }

    @Test
    fun `map to response NotAuthenticatedException with title and exceptionDetails`() {
        val e = NotAuthenticatedException("auth failed", exceptionDetails = ExceptionDetails("NotAuthenticatedException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(401, response.status)
        assertEquals("auth failed", response.message)
        assertEquals("NotAuthenticatedException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test ResourceNotFoundException response`() {
        val e = ResourceNotFoundException("User", "userlogin123", ExceptionDetails("ResourceNotFoundException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(404, response.status)
        assertEquals("User 'userlogin123' not found.", response.message)
        assertEquals("ResourceNotFoundException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test InternalServerException response`() {
        val e = InternalServerException("message", mapOf("detail" to "someinfo"), ExceptionDetails("InternalServerException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(500, response.status)
        assertEquals("message", response.message)
        assertEquals(3, response.details.size)
        assertEquals("someinfo", response.details["detail"])
        assertEquals("InternalServerException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test InvalidInputDataException response`() {
        val e = InvalidInputDataException("title", mapOf("detail" to "someinfo"), ExceptionDetails("InvalidInputDataException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(400, response.status)
        assertEquals("title", response.message)
        assertEquals(3, response.details.size)
        assertEquals("someinfo", response.details["detail"])
        assertEquals("InvalidInputDataException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test InvalidStateChangeException response`() {
        val e = InvalidStateChangeException("title", ExceptionDetails("InvalidStateChangeException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(409, response.status)
        assertEquals("title", response.message)
        assertEquals(2, response.details.size)
        assertEquals("InvalidStateChangeException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test OperationNotAllowedException response`() {
        val e = OperationNotAllowedException("title", ExceptionDetails("OperationNotAllowedException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(405, response.status)
        assertEquals("title", response.message)
        assertEquals(2, response.details.size)
        assertEquals("OperationNotAllowedException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test ResourceAlreadyExistsException response`() {
        val e = ResourceAlreadyExistsException("title", ExceptionDetails("ResourceAlreadyExistsException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(409, response.status)
        assertEquals("title", response.message)
        assertEquals(2, response.details.size)
        assertEquals("ResourceAlreadyExistsException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }

    @Test
    fun `test ServiceUnavailableException response`() {
        val e = ServiceUnavailableException("title", ExceptionDetails("ServiceUnavailableException", "Exception reason"))

        val response = HttpExceptionMapper.mapToResponse(e)

        assertEquals(503, response.status)
        assertEquals("title", response.message)
        assertEquals(2, response.details.size)
        assertEquals("ServiceUnavailableException", response.details["cause"])
        assertEquals("Exception reason", response.details["reason"])
    }
}
