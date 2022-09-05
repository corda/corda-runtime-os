package net.corda.ledger.consensual.impl.helper

import net.corda.internal.serialization.amqp.helper.TestSerializationService
import net.corda.ledger.consensual.impl.PartySerializer
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.cipher.suite.CipherSchemeMetadata

class ConfiguredTestSerializationService {
    companion object{
        fun getTestSerializationService(
            schemeMetadata: CipherSchemeMetadata
        ) : SerializationService =
            TestSerializationService.getTestSerializationService({
                it.register(PartySerializer(), it)
            }, schemeMetadata)
    }
}