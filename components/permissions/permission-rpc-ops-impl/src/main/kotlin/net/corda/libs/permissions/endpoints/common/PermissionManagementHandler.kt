package net.corda.libs.permissions.endpoints.common

import java.util.concurrent.TimeoutException
import net.corda.httprpc.exception.InternalServerException
import net.corda.httprpc.exception.InvalidInputDataException
import net.corda.httprpc.exception.ResourceNotFoundException
import net.corda.httprpc.exception.UnexpectedErrorException
import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException
import net.corda.libs.permissions.manager.PermissionManager
import net.corda.libs.permissions.manager.exception.UnexpectedPermissionResponseException
import net.corda.libs.permissions.manager.exception.RemotePermissionManagementException
import net.corda.messaging.api.exception.CordaRPCAPIResponderException
import net.corda.messaging.api.exception.CordaRPCAPISenderException
import org.slf4j.Logger

@Suppress("ComplexMethod", "ThrowsCount")
fun <T : Any> withPermissionManager(
    permissionManager: PermissionManager,
    logger: Logger,
    block: PermissionManager.() -> T?
): T? {
    return try {
        block.invoke(permissionManager)

    } catch (e: UnexpectedPermissionResponseException) {
        logger.warn("Permission manager failed to execute operation.")
        throw InternalServerException(e.message!!)

    } catch (e: RemotePermissionManagementException) {
        logger.warn("Remote permission management error: ${e.exceptionType}: ${e.message}")
        when (e.exceptionType) {
            EntityNotFoundException::class.java.name -> throw ResourceNotFoundException(e.message!!)
            EntityAssociationDoesNotExistException::class.java.name -> throw InvalidInputDataException(e.message!!)
            EntityAssociationAlreadyExistsException::class.java.name -> throw InvalidInputDataException(e.message!!)
            EntityAlreadyExistsException::class.java.name -> throw InvalidInputDataException(e.message!!)
            else -> throw InternalServerException(
                "Internal server error.",
                buildExceptionCauseDetails(e)
            )
        }

    } catch (e: CordaRPCAPISenderException) {
        logger.warn("Error during sending of permission management request.")
        throw InternalServerException(
            "Failed to send permission management request.",
            buildExceptionCauseDetails(e)
        )

    } catch (e: CordaRPCAPIResponderException) {
        logger.warn("Internal error reported from responder during permission management request.")
        throw InternalServerException(
            "Responder failed to send permission management request.",
            buildExceptionCauseDetails(e)
        )

    } catch (e: TimeoutException) {
        logger.warn("Permission management operation timed out.")
        throw InternalServerException(
            "Permission management operation timed out.",
            buildExceptionCauseDetails(e)
        )

    } catch (e: Exception) {
        logger.warn("Unexpected error during permission management operation.")
        throw UnexpectedErrorException(
            "Unexpected permission management error occurred.",
            buildExceptionCauseDetails(e)
        )

    }
}

private fun buildExceptionCauseDetails(e: Exception) = mapOf(
    "cause" to e::javaClass.name,
    "message" to (e.message ?: "")
)