package net.corda.libs.permissions.storage.writer.impl.validation

import net.corda.libs.permissions.common.exception.EntityAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationAlreadyExistsException
import net.corda.libs.permissions.common.exception.EntityAssociationDoesNotExistException
import net.corda.libs.permissions.common.exception.EntityNotFoundException

inline fun <T : Any> requireEntityExists(value: T?, lazyMessage: () -> Any): T {
    if (value == null) {
        val message = lazyMessage()
        throw EntityNotFoundException(message.toString())
    } else {
        return value
    }
}

inline fun <T : Any> requireEntityAssociationExists(value: T?, lazyMessage: () -> Any): T {
    if (value == null) {
        val message = lazyMessage()
        throw EntityAssociationDoesNotExistException(message.toString())
    } else {
        return value
    }
}

inline fun requireEntityAssociationDoesNotExist(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage()
        throw EntityAssociationAlreadyExistsException(message.toString())
    }
}

inline fun requireEntityDoesNotExist(value: Boolean, lazyMessage: () -> Any) {
    if (!value) {
        val message = lazyMessage()
        throw EntityAlreadyExistsException(message.toString())
    }
}