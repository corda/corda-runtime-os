package net.corda.internal.serialization

import net.corda.v5.serialization.SerializationContext
import net.corda.v5.serialization.SerializationFactory
import java.util.EnumSet

fun checkUseCase(allowedUseCases: EnumSet<SerializationContext.UseCase>) {
    val currentContext: SerializationContext = SerializationFactory.currentFactory?.currentContext
            ?: throw IllegalStateException("Current context is not set")
    if (!allowedUseCases.contains(currentContext.useCase)) {
        throw IllegalStateException("UseCase '${currentContext.useCase}' is not within '$allowedUseCases'")
    }
}

fun checkUseCase(allowedUseCase: SerializationContext.UseCase) {
    val currentContext: SerializationContext = SerializationFactory.currentFactory?.currentContext
            ?: throw IllegalStateException("Current context is not set")
    if (allowedUseCase != currentContext.useCase) {
        throw IllegalStateException("UseCase '${currentContext.useCase}' is not '$allowedUseCase'")
    }
}
