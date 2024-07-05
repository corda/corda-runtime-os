package net.corda.ledger.lib.dependencies.serialization

import net.corda.ledger.lib.impl.stub.serialization.StubSerializationService

object SerializationDependencies {
    val serializationService = StubSerializationService()
}