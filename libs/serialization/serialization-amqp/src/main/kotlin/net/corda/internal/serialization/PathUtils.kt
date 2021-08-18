package net.corda.internal.serialization

import net.corda.utilities.readAll
import net.corda.v5.application.services.serialization.deserialize
import java.nio.file.Path

/**
 * Read in this file as an AMQP serialised blob of type [T].
 * @see [deserialize]
 */
inline fun <reified T : Any> Path.readObject(serializationService: P2pSerializationService): T = serializationService.deserialize(readAll())