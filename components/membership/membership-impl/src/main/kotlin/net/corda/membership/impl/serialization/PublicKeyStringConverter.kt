package net.corda.membership.impl.serialization

import net.corda.v5.application.node.StringObjectConverter
import net.corda.v5.cipher.suite.KeyEncodingService
import java.security.PublicKey

class PublicKeyStringConverter(val encodingService: KeyEncodingService): StringObjectConverter<PublicKey> {
    override val keyEncodingService: KeyEncodingService get() = encodingService
    override fun convert(stringProperties: Map<String, String>): PublicKey {
        return keyEncodingService.decodePublicKey(stringProperties.values.first())
    }
}