package net.corda.membership.application.converter

import net.corda.layeredpropertymap.ConversionContext
import net.corda.layeredpropertymap.CustomPropertyConverter
import net.corda.membership.application.PartyImpl
import net.corda.v5.application.identity.Party
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
        private const val OWNING_KEY = "owningKey"
    }

    override val type: Class<Party>
        get() = Party::class.java

    override fun convert(context: ConversionContext): Party =
        PartyImpl(
            name = context.value(NAME)?.let {
                MemberX500Name.parse(it)
            } ?: throw ValueNotFoundException("'$NAME' is null or missing"),
            owningKey = context.value(OWNING_KEY)?.let {
                keyEncodingService.decodePublicKey(it)
            } ?: throw ValueNotFoundException("'$OWNING_KEY' is null or missing")
        )
}