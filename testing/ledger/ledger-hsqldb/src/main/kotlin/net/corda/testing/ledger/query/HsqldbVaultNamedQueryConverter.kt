package net.corda.testing.ledger.query

import java.util.LinkedList
import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.BinaryKeyword
import net.corda.ledger.persistence.query.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.JsonCast
import net.corda.ledger.persistence.query.parsing.JsonField
import net.corda.ledger.persistence.query.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.RightParenthesis
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Token
import net.corda.ledger.persistence.query.parsing.converters.AbstractVaultNamedQueryConverterImpl
import net.corda.ledger.persistence.query.parsing.converters.VaultNamedQueryConverter
import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.HSQLDB_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Component(service = [ VaultNamedQueryConverter::class ])
class HsqldbVaultNamedQueryConverter @Activate constructor(
    @Reference(target = HSQLDB_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
) : AbstractVaultNamedQueryConverterImpl() {
    private companion object {
        private const val ANSI_CAST = " CAST("
        private val JSON_COLUMN_TYPE = listOf(SqlType(JSON_SQL_TYPE))

        private fun Collection<Token>.toStringLiteral(): LinkedList<Token> {
            return mapTo(LinkedList()) { token ->
                if (token is PathReference && !token.ref.let { it.startsWith('"') || it.startsWith('\'') }) {
                    PathReferenceWithSpaces("'${token.ref}'")
                } else {
                    token
                }
            }
        }
    }

    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    private fun writeWithCast(output: StringBuilder, tokens: List<Token>) {
        if (tokens.size == 1 && tokens[0].let { it is Parameter || it is PathReference }) {
            output.append(ANSI_CAST)
            write(output, listOf(As(tokens, JSON_COLUMN_TYPE), RightParenthesis))
        } else {
            write(output, tokens)
        }
    }

    private fun writeJsonFunction(output: StringBuilder, text: String, keyword: BinaryKeyword) {
        output.append(text)
        writeWithCast(output, keyword.op1)
        write(output, ParameterEnd)
        write(output, keyword.op2.toStringLiteral())
        write(output, RightParenthesis)
    }

    override fun writeCustom(output: StringBuilder, token: Token) {
        when (token) {
            is JsonField -> writeJsonFunction(output, " JsonFieldAsObject(", token)
            is JsonArrayOrObjectAsText -> writeJsonFunction(output, " JsonFieldAsText(", token)
            is JsonKeyExists -> writeJsonFunction(output, " HasJsonKey(", token)
            is JsonCast -> {
                output.append(ANSI_CAST)
                write(output, listOf(As(token.op1, token.op2), RightParenthesis))
            }
            else ->
                super.writeCustom(output, token)
        }
    }
}
