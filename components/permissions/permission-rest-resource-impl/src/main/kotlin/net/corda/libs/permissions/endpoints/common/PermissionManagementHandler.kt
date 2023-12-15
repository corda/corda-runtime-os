package net.corda.libs.permissions.endpoints.common

import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.messaging.api.exception.CordaRPCAPIPartitionException
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import net.corda.rest.exception.HttpApiException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.ResourceAlreadyExistsException
import net.corda.rest.exception.ResourceNotFoundException
import net.corda.rest.exception.ServiceUnavailableException
import org.slf4j.Logger
import java.util.concurrent.TimeoutException

@Suppress("ComplexMethod", "ThrowsCount")
fun <T : Any?> withPermissionManager(
    permissionManager: PermissionManager,
    logger: Logger,
    block: PermissionManager.() -> T
): T {
    return try {
        block.invoke(permissionManager)
    } catch (e: HttpApiException) {
        // This is already a well-formed exception - rethrow
        throw e
    } catch (e: UnexpectedPermissionResponseException) {
        logger.warn("Permission manager received an unexpected response: ${e::class.java.name}: ${e.message}")
        throw InternalServerException(
            details = buildExceptionCauseDetails(e)
        )
    } catch (e: RemotePermissionManagementException) {
        logger.warn("Remote permission management error: ${e.exceptionType}: ${e.message}")
        when (e.exceptionType) {
            EntityNotFoundException::class.java.name -> throw ResourceNotFoundException(e.message!!)
            EntityAssociationDoesNotExistException::class.java.name -> throw InvalidInputDataException(e.message!!)
            EntityAssociationAlreadyExistsException::class.java.name -> throw ResourceAlreadyExistsException(e.message!!)
            EntityAlreadyExistsException::class.java.name -> throw ResourceAlreadyExistsException(e.message!!)
            else -> throw InternalServerException(
                details = buildExceptionCauseDetails(e.exceptionType, e.message ?: "Remote permission management error occurred.")
            )
        }
    } catch (e: CordaRPCAPIPartitionException) {
        logger.warn("Error waiting for permission management response.", e)
        throw ServiceUnavailableException("Error waiting for permission management response: Repartition Event!")
    } catch (e: CordaRPCAPISenderException) {
        logger.warn("Error during sending of permission management request.", e)
        throw InternalServerException(
            details = buildExceptionCauseDetails(e.cause ?: e)
        )
    } catch (e: CordaRPCAPIResponderException) {
        logger.warn("Permission manager received error from responder: ${e.message}", e.cause)
        throw InternalServerException(
            details = buildExceptionCauseDetails(e.cause ?: e)
        )
    } catch (e: TimeoutException) {
        logger.warn("Permission management operation timed out.", e)
        throw InternalServerException("Permission management operation timed out.")
    } catch (e: Exception) {
        logger.warn("Unexpected error during permission management operation.", e)
        throw InternalServerException(
            "Unexpected permission management error occurred.",
            details = buildExceptionCauseDetails(e)
        )
    }
}

private fun buildExceptionCauseDetails(e: Throwable) = mapOf(
    "cause" to e::class.java.name,
    "reason" to (e.message ?: "")
)

private fun buildExceptionCauseDetails(type: String, reason: String) = mapOf(
    "cause" to type,
    "reason" to reason
)
