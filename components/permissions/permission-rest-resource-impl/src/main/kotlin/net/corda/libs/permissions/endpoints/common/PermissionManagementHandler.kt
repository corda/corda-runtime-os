package net.corda.libs.permissions.endpoints.common

import net.corda.libs.permissions.common.exception.ConcurrentEntityModificationException
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
import net.corda.rest.exception.ExceptionDetails
import net.corda.rest.exception.HttpApiException
import net.corda.rest.exception.InternalServerException
import net.corda.rest.exception.InvalidInputDataException
import net.corda.rest.exception.InvalidStateChangeException
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
            exceptionDetails = ExceptionDetails(e::class.java.name, "${e.message}")
        )
    } catch (e: RemotePermissionManagementException) {
        logger.warn("Remote permission management error: ${e.exceptionType}: ${e.message}")
        val exceptionSimpleName = e::class.java.simpleName
        when (e.exceptionType) {
            EntityNotFoundException::class.java.name -> throw ResourceNotFoundException(
                exceptionSimpleName,
                ExceptionDetails(e.exceptionType, e.message!!)
            )
            EntityAssociationDoesNotExistException::class.java.name -> throw InvalidInputDataException(
                title = exceptionSimpleName,
                exceptionDetails = ExceptionDetails(e.exceptionType, e.message!!)
            )
            EntityAssociationAlreadyExistsException::class.java.name,
            EntityAlreadyExistsException::class.java.name -> throw ResourceAlreadyExistsException(
                exceptionSimpleName,
                ExceptionDetails(e.exceptionType, e.message!!)
            )
            ConcurrentEntityModificationException::class.java.name -> throw InvalidStateChangeException(
                exceptionSimpleName,
                ExceptionDetails(e.exceptionType, e.message!!)
            )
            else -> throw InternalServerException(
                exceptionDetails = ExceptionDetails(e.exceptionType, e.message ?: "Remote permission management error occurred.")
            )
        }
    } catch (e: CordaRPCAPIPartitionException) {
        logger.warn("Error waiting for permission management response.", e)
        throw ServiceUnavailableException(
            "Error waiting for permission management response: Repartition Event!",
            ExceptionDetails(e::class.java.name, "${e.message}")
        )
    } catch (e: CordaRPCAPISenderException) {
        logger.warn("Error during sending of permission management request.", e)
        val ex = e.cause ?: e
        throw InternalServerException(
            exceptionDetails = ExceptionDetails(ex::class.java.name, "${ex.message}")
        )
    } catch (e: CordaRPCAPIResponderException) {
        logger.warn("Permission manager received error from responder: ${e.message}", e.cause)
        val ex = e.cause ?: e
        throw InternalServerException(
            exceptionDetails = ExceptionDetails(ex::class.java.name, "${ex.message}")
        )
    } catch (e: TimeoutException) {
        logger.warn("Permission management operation timed out.", e)
        throw InternalServerException(
            title = "Permission management operation timed out.",
            exceptionDetails = ExceptionDetails(e::class.java.name, "${e.message}")
        )
    } catch (e: Exception) {
        logger.warn("Unexpected error during permission management operation.", e)
        throw InternalServerException(
            title = "Unexpected permission management error occurred.",
            exceptionDetails = ExceptionDetails(e::class.java.name, "${e.message}")
        )
    }
}
