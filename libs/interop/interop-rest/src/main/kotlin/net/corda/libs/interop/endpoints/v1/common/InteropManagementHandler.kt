package net.corda.libs.interop.endpoints.v1.common

import net.corda.libs.interop.endpoints.v1.InteropManager
import org.slf4j.Logger

@Suppress("ComplexMethod", "ThrowsCount", "UNUSED_PARAMETER")
fun <T : Any?> withInteropManager(
    interopManager: InteropManager,
    logger: Logger,
    block: InteropManager.() -> T
): T {
    TODO("implement")
//    return try {
//        block.invoke(interopManager)
//
//    }
//    catch (e: HttpApiException) {
//        // This is already a well-formed exception - rethrow
//        throw e
//
//    }
//    catch (e: UnexpectedPermissionResponseException) {
//        logger.warn("Permission manager received an unexpected response: ${e::class.java.name}: ${e.message}")
//        throw InternalServerException(
//            details = buildExceptionCauseDetails(e)
//        )
//
//    } catch (e: RemotePermissionManagementException) {
//        logger.warn("Remote permission management error: ${e.exceptionType}: ${e.message}")
//        when (e.exceptionType) {
//            EntityNotFoundException::class.java.name -> throw ResourceNotFoundException(e.message!!)
//            EntityAssociationDoesNotExistException::class.java.name -> throw InvalidInputDataException(e.message!!)
//            EntityAssociationAlreadyExistsException::class.java.name -> throw ResourceAlreadyExistsException(e.message!!)
//            EntityAlreadyExistsException::class.java.name -> throw ResourceAlreadyExistsException(e.message!!)
//            else -> throw InternalServerException(
//                details = buildExceptionCauseDetails(e.exceptionType, e.message ?: "Remote permission management error occurred.")
//            )
//        }
//
//    } catch (e: CordaRPCAPIPartitionException) {
//        logger.warn("Error waiting for permission management response.", e)
//        throw ServiceUnavailableException("Error waiting for permission management response: Repartition Event!")
//
//    } catch (e: CordaRPCAPISenderException) {
//        logger.warn("Error during sending of permission management request.", e)
//        throw InternalServerException(
//            details = buildExceptionCauseDetails(e.cause ?: e)
//        )
//
//    } catch (e: CordaRPCAPIResponderException) {
//        logger.warn("Permission manager received error from responder: ${e.message}", e.cause)
//        throw InternalServerException(
//            details = buildExceptionCauseDetails(e.cause ?: e)
//        )
//
//    }
//    catch (e: TimeoutException) {
//        logger.warn("Permission management operation timed out.", e)
//        throw InternalServerException("Permission management operation timed out.")
//
//    }
//    catch (e: Exception) {
//        logger.warn("Unexpected error during permission management operation.", e)
//        throw InternalServerException(
//            "Unexpected permission management error occurred.",
//            details = buildExceptionCauseDetails(e)
//        )
//
//    }
}

private fun buildExceptionCauseDetails(e: Throwable) = mapOf(
    "cause" to e::class.java.name,
    "reason" to (e.message ?: "")
)

private fun buildExceptionCauseDetails(type: String, reason: String) = mapOf(
    "cause" to type,
    "reason" to reason
)