package net.corda.ledger.persistence.query.impl.parsing

import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.VaultNamedQueryParserImpl
import net.corda.ledger.persistence.query.parsing.Where
import net.corda.ledger.persistence.query.parsing.converters.VaultNamedQueryConverter
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VaultNamedQueryParserImplTest {

    private companion object {
        const val QUERY = "my query"
        val PATH_REFERENCE = PathReference("field")
    }

    private val expressionParser = mock<VaultNamedQueryExpressionParser>()
    private val expressionValidator = mock<VaultNamedQueryExpressionValidator>()
    private val converter = mock<VaultNamedQueryConverter>()
    private val stringBuilderCaptor = argumentCaptor<StringBuilder>()
    private val vaultNamedQueryParser = VaultNamedQueryParserImpl(expressionParser, expressionValidator, converter)

    @Test
    fun `parses query and validates it`() {
        val condition = listOf(PATH_REFERENCE)
        val expression = listOf(Where(condition))
        val output = "output"
        whenever(expressionParser.parse(QUERY)).thenReturn(expression)
        whenever(expressionValidator.validateWhereJson(QUERY, expression)).thenReturn(condition)
        whenever(
            converter.convert(
                stringBuilderCaptor.capture(),
                any()
            )
        ).then { stringBuilderCaptor.firstValue.append(output) }
        assertThat(vaultNamedQueryParser.parseWhereJson(QUERY)).isEqualTo(output)
        verify(expressionParser).parse(QUERY)
        verify(expressionValidator).validateWhereJson(QUERY, expression)
        verify(converter).convert(any(), eq(condition))
    }

    @Test
    fun `repeated spaces and leading and trailing whitespace are not included in the output`() {
        whenever(expressionParser.parse(any())).thenReturn(listOf())
        whenever(
            converter.convert(
                stringBuilderCaptor.capture(),
                any()
            )
        ).then { stringBuilderCaptor.firstValue.append(" SELECT  FROM  WHERE  IS NOT NULL ") }
        assertThat(vaultNamedQueryParser.parseWhereJson(QUERY)).isEqualTo("SELECT FROM WHERE IS NOT NULL")
    }
}
