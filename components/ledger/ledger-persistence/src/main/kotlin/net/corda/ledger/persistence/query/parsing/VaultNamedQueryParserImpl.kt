package net.corda.ledger.persistence.query.parsing

import net.corda.ledger.persistence.query.parsing.converters.VaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParserImpl
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [VaultNamedQueryParser::class])
class VaultNamedQueryParserImpl(
    private val delegate: VaultNamedQueryParser
) : VaultNamedQueryParser by delegate {
    @Activate
    constructor(
        @Reference
        converter: VaultNamedQueryConverter
    ) : this(MyVaultNamedQueryParserImpl(VaultNamedQueryExpressionParserImpl(), VaultNamedQueryExpressionValidatorImpl(), converter))
}
