package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.impl.parsing.From
import net.corda.ledger.persistence.query.impl.parsing.Number
import net.corda.ledger.persistence.query.impl.parsing.PathReference
import net.corda.ledger.persistence.query.impl.parsing.Select
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class VaultNamedQueryExpressionValidatorImplTest {

    private val validator = VaultNamedQueryExpressionValidatorImpl()

    @Test
    fun `acceptable expression does not throw an exception`() {
        assertDoesNotThrow { validator.validateWhereJson("my query", listOf(PathReference("field"), Number("1"))) }
    }

    @Test
    fun `expression containing a select token throws an exception`() {
        assertThatThrownBy { validator.validateWhereJson("my query", listOf(PathReference("field"), Select())) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot contain the SELECT keyword")
            .hasMessageContaining("my query")
    }

    @Test
    fun `expression containing a from token throws an exception`() {
        assertThatThrownBy { validator.validateWhereJson("my query", listOf(PathReference("field"), From())) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("cannot contain the FROM keyword")
            .hasMessageContaining("my query")
    }
}