package net.corda.membership.impl.serialization

import net.corda.data.identity.HoldingIdentity
import net.corda.v5.application.node.StringObjectConverter
import net.corda.v5.cipher.suite.KeyEncodingService

class HoldingIdentityStringConverter(override val keyEncodingService: KeyEncodingService): StringObjectConverter<HoldingIdentity> {
    override fun convert(stringProperties: Map<String, String>): HoldingIdentity {
        return HoldingIdentity()
    }
}