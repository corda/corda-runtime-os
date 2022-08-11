package net.corda.ledger.consensual.impl.converter

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.v5.ledger.consensual.Party
import net.corda.v5.base.exceptions.ValueNotFoundException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.cipher.suite.KeyEncodingService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * Converter class, converting from String to [Party] object.
 */
@Component(service = [CustomPropertyConverter::class])
class PartyConverter @Activate constructor(
    @Reference(service = KeyEncodingService::class)
    private val keyEncodingService: KeyEncodingService
) : CustomPropertyConverter<Party> {
    companion object {
        private const val NAME = "name"
        private const val SESSION_KEY = "session.key"
    }

    override val type: Class<Party>
        get() = Party::class.java

    override fun convert(context: ConversionContext): Party =
        Party(
            name = context.value(NAME)?.let {
                MemberX500Name.parse(it)
            } ?: throw ValueNotFoundException("'$NAME' is null or missing"),
            owningKey = context.value(SESSION_KEY)?.let {
                keyEncodingService.decodePublicKey(it)
            } ?: throw ValueNotFoundException("'$SESSION_KEY' is null or missing")
        )
}