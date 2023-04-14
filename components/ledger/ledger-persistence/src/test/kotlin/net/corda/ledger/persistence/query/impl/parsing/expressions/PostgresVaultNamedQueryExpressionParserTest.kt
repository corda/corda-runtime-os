package net.corda.ledger.persistence.query.impl.parsing.expressions

import net.corda.ledger.persistence.query.parsing.And
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.Equals
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.GreaterThan
import net.corda.ledger.persistence.query.parsing.GreaterThanEquals
import net.corda.ledger.persistence.query.parsing.In
import net.corda.ledger.persistence.query.parsing.IsNotNull
import net.corda.ledger.persistence.query.parsing.IsNull
import net.corda.ledger.persistence.query.parsing.JsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.JsonCast
import net.corda.ledger.persistence.query.parsing.JsonField
import net.corda.ledger.persistence.query.parsing.JsonKeyExists
import net.corda.ledger.persistence.query.parsing.LeftParentheses
import net.corda.ledger.persistence.query.parsing.LessThan
import net.corda.ledger.persistence.query.parsing.LessThanEquals
import net.corda.ledger.persistence.query.parsing.Like
import net.corda.ledger.persistence.query.parsing.NotEquals
import net.corda.ledger.persistence.query.parsing.Number
import net.corda.ledger.persistence.query.parsing.Or
import net.corda.ledger.persistence.query.parsing.Parameter
import net.corda.ledger.persistence.query.parsing.ParameterEnd
import net.corda.ledger.persistence.query.parsing.PathReference
import net.corda.ledger.persistence.query.parsing.PathReferenceWithSpaces
import net.corda.ledger.persistence.query.parsing.RightParentheses
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.Where
import net.corda.ledger.persistence.query.parsing.expressions.PostgresVaultNamedQueryExpressionParser
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Index
import org.junit.jupiter.api.Test

class PostgresVaultNamedQueryExpressionParserTest {

    private val expressionParser = PostgresVaultNamedQueryExpressionParser()

    @Test
    fun `field name not in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("these ARE 123 field nAmEs != ->> is null is not null")
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(4)
        assertThat(expression)
            .contains(PathReference("these"), Index.atIndex(0))
            .contains(PathReference("ARE"), Index.atIndex(1))
            .contains(PathReference("field"), Index.atIndex(3))
            .contains(PathReference("nAmEs"), Index.atIndex(4))
    }

    @Test
    fun `field name in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("'these' 'ARE' 123 '456' 'field' 'nAmEs.' != ->> '->>' is null is not null")
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(6)
        assertThat(expression)
            .contains(PathReference("'these'"), Index.atIndex(0))
            .contains(PathReference("'ARE'"), Index.atIndex(1))
            .contains(PathReference("'456'"), Index.atIndex(3))
            .contains(PathReference("'field'"), Index.atIndex(4))
            .contains(PathReference("'nAmEs.'"), Index.atIndex(5))
            .contains(PathReference("'->>'"), Index.atIndex(8))
    }

    @Test
    fun `field name in quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("'these ARE' 123 '456 789' 'field nAmEs.' != ->> '->> is null' is not null")
        assertThat(expression.filterIsInstance<PathReferenceWithSpaces>()).hasSize(4)
        assertThat(expression)
            .contains(PathReferenceWithSpaces("'these ARE'"), Index.atIndex(0))
            .contains(PathReferenceWithSpaces("'456 789'"), Index.atIndex(2))
            .contains(PathReferenceWithSpaces("'field nAmEs.'"), Index.atIndex(3))
            .contains(PathReferenceWithSpaces("'->> is null'"), Index.atIndex(6))
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("\"these\" \"ARE\" 123 \"456\" \"field\" \"nAmEs.\" != ->> \"->>\" is null is not null")
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(6)
        assertThat(expression)
            .contains(PathReference("\"these\""), Index.atIndex(0))
            .contains(PathReference("\"ARE\""), Index.atIndex(1))
            .contains(PathReference("\"456\""), Index.atIndex(3))
            .contains(PathReference("\"field\""), Index.atIndex(4))
            .contains(PathReference("\"nAmEs.\""), Index.atIndex(5))
            .contains(PathReference("\"->>\""), Index.atIndex(8))
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("\"these ARE\" 123 \"456 789\" \"field nAmEs.\" != ->> \"->> is null\" is not null")
        assertThat(expression.filterIsInstance<PathReferenceWithSpaces>()).hasSize(4)
        assertThat(expression)
            .contains(PathReferenceWithSpaces("\"these ARE\""), Index.atIndex(0))
            .contains(PathReferenceWithSpaces("\"456 789\""), Index.atIndex(2))
            .contains(PathReferenceWithSpaces("\"field nAmEs.\""), Index.atIndex(3))
            .contains(PathReferenceWithSpaces("\"->> is null\""), Index.atIndex(6))
    }

    @Test
    fun `parameter name is parsed as Parameter`() {
        val expression = expressionParser.parse(":parameter ARE 123 :another_one nAmEs != :with-dashes ::int ->> is null is not null")
        assertThat(expression.filterIsInstance<Parameter>()).hasSize(3)
        assertThat(expression)
            .contains(Parameter(":parameter"), Index.atIndex(0))
            .contains(Parameter(":another_one"), Index.atIndex(3))
            .contains(Parameter(":with-dashes"), Index.atIndex(6))

    }

    @Test
    fun `number with no decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1 23456 field nAmEs 78910 != ->> is null is not null")
        assertThat(expression.filterIsInstance<Number>()).hasSize(3)
        assertThat(expression)
            .contains(Number("1"), Index.atIndex(0))
            .contains(Number("23456"), Index.atIndex(1))
            .contains(Number("78910"), Index.atIndex(4))
    }

    @Test
    fun `number with decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1.0 23.456 field nAmEs 78910.00000001 != ->> is null is not null")
        assertThat(expression.filterIsInstance<Number>()).hasSize(3)
        assertThat(expression)
            .contains(Number("1.0"), Index.atIndex(0))
            .contains(Number("23.456"), Index.atIndex(1))
            .contains(Number("78910.00000001"), Index.atIndex(4))
    }

    /**
     * JSON field operator
     */
    @Test
    fun `json field is parsed as JsonArrayOrObjectAsText`() {
        val expression = expressionParser.parse("these ARE -> field nAmEs != ->> -> is null is not null")
        assertThat(expression.filterIsInstance<JsonField>()).hasSize(2)
        assertThat(expression)
            .contains(JsonField(), Index.atIndex(2))
            .contains(JsonField(), Index.atIndex(7))
    }

    /**
     * JSON array or object to text = "->>"
     */
    @Test
    fun `json array or object to text is parsed as JsonArrayOrObjectAsText`() {
        val expression = expressionParser.parse("these ARE ->> field nAmEs != ->> is null is not null")
        assertThat(expression.filterIsInstance<JsonArrayOrObjectAsText>()).hasSize(2)
        assertThat(expression)
            .contains(JsonArrayOrObjectAsText(), Index.atIndex(2))
            .contains(JsonArrayOrObjectAsText(), Index.atIndex(6))
    }

    @Test
    fun `as is parsed (case insensitive) as As`() {
        val expression = expressionParser.parse("asasas AS ->> as aS zasz != ->> is null is not null")
        assertThat(expression.filterIsInstance<As>()).hasSize(3)
        assertThat(expression)
            .contains(As(), Index.atIndex(1))
            .contains(As(), Index.atIndex(3))
            .contains(As(), Index.atIndex(4))
    }

    @Test
    fun `from is parsed (case insensitive) as From`() {
        val expression = expressionParser.parse("fromfromfrom FROM ->> from frOM zfromz != ->> is null is not null")
        assertThat(expression.filterIsInstance<From>()).hasSize(3)
        assertThat(expression)
            .contains(From(), Index.atIndex(1))
            .contains(From(), Index.atIndex(3))
            .contains(From(), Index.atIndex(4))
    }

    @Test
    fun `select is parsed (case insensitive) as Select`() {
        val expression = expressionParser.parse("selectselectselect SELECT ->> select seLEcT zselectz != ->> is null is not null")
        assertThat(expression.filterIsInstance<Select>()).hasSize(3)
        assertThat(expression)
            .contains(Select(), Index.atIndex(1))
            .contains(Select(), Index.atIndex(3))
            .contains(Select(), Index.atIndex(4))
    }

    @Test
    fun `where is parsed (case insensitive) as Where`() {
        val expression = expressionParser.parse("wherewherewhere WHERE ->> where wheRE zwherez != ->> is null is not null")
        assertThat(expression.filterIsInstance<Where>()).hasSize(3)
        assertThat(expression)
            .contains(Where(), Index.atIndex(1))
            .contains(Where(), Index.atIndex(3))
            .contains(Where(), Index.atIndex(4))
    }

    @Test
    fun `and is parsed (case insensitive) as And`() {
        val expression = expressionParser.parse("andandand AND ->> and ANd zandz != ->> is null is not null")
        assertThat(expression.filterIsInstance<And>()).hasSize(3)
        assertThat(expression)
            .contains(And(), Index.atIndex(1))
            .contains(And(), Index.atIndex(3))
            .contains(And(), Index.atIndex(4))
    }

    @Test
    fun `or is parsed (case insensitive) as Or`() {
        val expression = expressionParser.parse("ororor OR ->> or Or zorz != ->> is null is not null")
        assertThat(expression.filterIsInstance<Or>()).hasSize(3)
        assertThat(expression)
            .contains(Or(), Index.atIndex(1))
            .contains(Or(), Index.atIndex(3))
            .contains(Or(), Index.atIndex(4))
    }

    @Test
    fun `is null is parsed (case insensitive) as IsNull`() {
        val expression = expressionParser.parse("is nullis nullis null IS NULL ->> is null Is NuLL zis nullz != ->> is not null")
        assertThat(expression.filterIsInstance<IsNull>()).hasSize(3)
        assertThat(expression)
            .contains(IsNull(), Index.atIndex(4))
            .contains(IsNull(), Index.atIndex(6))
            .contains(IsNull(), Index.atIndex(7))
    }

    @Test
    fun `is not null is parsed (case insensitive) as IsNotNull`() {
        val expression =
            expressionParser.parse("is not nullis not nullis not null IS NOT NULL ->> is not null Is NoT NuLL zis not nullz != ->> is null")
        assertThat(expression.filterIsInstance<IsNotNull>()).hasSize(3)
        assertThat(expression)
            .contains(IsNotNull(), Index.atIndex(7))
            .contains(IsNotNull(), Index.atIndex(9))
            .contains(IsNotNull(), Index.atIndex(10))
    }

    /**
     * !in or not in??
     */
    @Test
    fun `in is parsed (case insensitive) as In`() {
        val expression = expressionParser.parse("ininin IN ->> in iN zinz != ->> is null is not null")
        assertThat(expression.filterIsInstance<In>()).hasSize(3)
        assertThat(expression)
            .contains(In(), Index.atIndex(1))
            .contains(In(), Index.atIndex(3))
            .contains(In(), Index.atIndex(4))
    }

    @Test
    fun `equals is parsed as Equals`() {
        val expression = expressionParser.parse("=== = ->> = = z=z != ->> is null is not null")
        assertThat(expression.filterIsInstance<Equals>()).hasSize(7)
        assertThat(expression)
            .contains(Equals(), Index.atIndex(0))
            .contains(Equals(), Index.atIndex(1))
            .contains(Equals(), Index.atIndex(2))
            .contains(Equals(), Index.atIndex(3))
            .contains(Equals(), Index.atIndex(5))
            .contains(Equals(), Index.atIndex(6))
            .contains(Equals(), Index.atIndex(8))
    }

    @Test
    fun `not equals is parsed as NotEquals`() {
        val expression = expressionParser.parse("!=!=!= != ->> != != z!=z ->> is null is not null")
        assertThat(expression.filterIsInstance<NotEquals>()).hasSize(7)
        assertThat(expression)
            .contains(NotEquals(), Index.atIndex(0))
            .contains(NotEquals(), Index.atIndex(1))
            .contains(NotEquals(), Index.atIndex(2))
            .contains(NotEquals(), Index.atIndex(3))
            .contains(NotEquals(), Index.atIndex(5))
            .contains(NotEquals(), Index.atIndex(6))
            .contains(NotEquals(), Index.atIndex(8))
    }

    @Test
    fun `less than is parsed as LessThan`() {
        val expression = expressionParser.parse("<<< < ->> < < z<z != ->> is null is not null")
        assertThat(expression.filterIsInstance<LessThan>()).hasSize(7)
        assertThat(expression)
            .contains(LessThan(), Index.atIndex(0))
            .contains(LessThan(), Index.atIndex(1))
            .contains(LessThan(), Index.atIndex(2))
            .contains(LessThan(), Index.atIndex(3))
            .contains(LessThan(), Index.atIndex(5))
            .contains(LessThan(), Index.atIndex(6))
            .contains(LessThan(), Index.atIndex(8))
    }

    @Test
    fun `less than or equal to is parsed as LessThanEquals`() {
        val expression = expressionParser.parse("<=<=<= <= ->> <= <= z<=z != ->> is null is not null")
        assertThat(expression.filterIsInstance<LessThanEquals>()).hasSize(7)
        assertThat(expression)
            .contains(LessThanEquals(), Index.atIndex(0))
            .contains(LessThanEquals(), Index.atIndex(1))
            .contains(LessThanEquals(), Index.atIndex(2))
            .contains(LessThanEquals(), Index.atIndex(3))
            .contains(LessThanEquals(), Index.atIndex(5))
            .contains(LessThanEquals(), Index.atIndex(6))
            .contains(LessThanEquals(), Index.atIndex(8))
    }

    @Test
    fun `greater than is parsed as GreaterThan`() {
        val expression = expressionParser.parse(">>> > ->> > > z>z != ->> is null is not null")
        assertThat(expression.filterIsInstance<GreaterThan>()).hasSize(7)
        assertThat(expression)
            .contains(GreaterThan(), Index.atIndex(0))
            .contains(GreaterThan(), Index.atIndex(1))
            .contains(GreaterThan(), Index.atIndex(2))
            .contains(GreaterThan(), Index.atIndex(3))
            .contains(GreaterThan(), Index.atIndex(5))
            .contains(GreaterThan(), Index.atIndex(6))
            .contains(GreaterThan(), Index.atIndex(8))
    }

    @Test
    fun `greater than or equal to is parsed as GreaterThanEquals`() {
        val expression = expressionParser.parse(">=>=>= >= ->> >= >= z>=z != ->> is null is not null")
        assertThat(expression.filterIsInstance<GreaterThanEquals>()).hasSize(7)
        assertThat(expression)
            .contains(GreaterThanEquals(), Index.atIndex(0))
            .contains(GreaterThanEquals(), Index.atIndex(1))
            .contains(GreaterThanEquals(), Index.atIndex(2))
            .contains(GreaterThanEquals(), Index.atIndex(3))
            .contains(GreaterThanEquals(), Index.atIndex(5))
            .contains(GreaterThanEquals(), Index.atIndex(6))
            .contains(GreaterThanEquals(), Index.atIndex(8))
    }

    @Test
    fun `json cast to is parsed as JsonCast`() {
        val expression = expressionParser.parse("::int::int::int ::date ::string->> ::int=5 ::int>5 z::intz<=1 != ->> is null is not null")
        assertThat(expression.filterIsInstance<JsonCast>()).hasSize(6)
        assertThat(expression)
            .contains(JsonCast("int::int::int"), Index.atIndex(0))
            .contains(JsonCast("date"), Index.atIndex(1))
            .contains(JsonCast("string"), Index.atIndex(2))
            .contains(JsonCast("int"), Index.atIndex(4))
            .contains(JsonCast("int"), Index.atIndex(7))
            .contains(JsonCast("intz"), Index.atIndex(11))
    }

    @Test
    fun `json key exist is parsed as JsonKeyExists`() {
        val expression = expressionParser.parse("??? ? ->> ? ? z?z ->> is null is not null")
        assertThat(expression.filterIsInstance<JsonKeyExists>()).hasSize(7)
        assertThat(expression)
            .contains(JsonKeyExists(), Index.atIndex(0))
            .contains(JsonKeyExists(), Index.atIndex(1))
            .contains(JsonKeyExists(), Index.atIndex(2))
            .contains(JsonKeyExists(), Index.atIndex(3))
            .contains(JsonKeyExists(), Index.atIndex(5))
            .contains(JsonKeyExists(), Index.atIndex(6))
            .contains(JsonKeyExists(), Index.atIndex(8))
    }

    @Test
    fun `like is parsed as Like`() {
        val expression = expressionParser.parse("likelikelike LIKE ->> like LiKe zlikez != ->> is null is not null")
        assertThat(expression.filterIsInstance<Like>()).hasSize(3)
        assertThat(expression)
            .contains(Like(), Index.atIndex(1))
            .contains(Like(), Index.atIndex(3))
            .contains(Like(), Index.atIndex(4))
    }

    @Test
    fun `spaces are ignored`() {
        val expression = expressionParser.parse("  spaces spaces  spaces      spaces ")
        assertThat(expression).hasSize(4)
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(4)
    }

    @Test
    fun `tabs are ignored`() {
        val expression = expressionParser.parse("\t\tspaces\tspaces \t spaces \t \t spaces \t")
        assertThat(expression).hasSize(4)
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(4)
    }

    @Test
    fun `new lines are ignored`() {
        val expression = expressionParser.parse("\n\nspaces\nspaces \n spaces \n \n spaces \n")
        assertThat(expression).hasSize(4)
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(4)
    }

    @Test
    fun `commas are parsed as ParameterEnds`() {
        val expression = expressionParser.parse(",,, , ->> , , z,z != ->> is null is not null")
        assertThat(expression.filterIsInstance<ParameterEnd>()).hasSize(7)
        assertThat(expression)
            .contains(ParameterEnd(), Index.atIndex(0))
            .contains(ParameterEnd(), Index.atIndex(1))
            .contains(ParameterEnd(), Index.atIndex(2))
            .contains(ParameterEnd(), Index.atIndex(3))
            .contains(ParameterEnd(), Index.atIndex(5))
            .contains(ParameterEnd(), Index.atIndex(6))
            .contains(ParameterEnd(), Index.atIndex(8))
    }

    @Test
    fun `left parentheses are parsed as LeftParentheses`() {
        val expression = expressionParser.parse("((( ( ->> ( ( z(z != ->> is null is not null")
        assertThat(expression.filterIsInstance<LeftParentheses>()).hasSize(7)
        assertThat(expression)
            .contains(LeftParentheses(), Index.atIndex(0))
            .contains(LeftParentheses(), Index.atIndex(1))
            .contains(LeftParentheses(), Index.atIndex(2))
            .contains(LeftParentheses(), Index.atIndex(3))
            .contains(LeftParentheses(), Index.atIndex(5))
            .contains(LeftParentheses(), Index.atIndex(6))
            .contains(LeftParentheses(), Index.atIndex(8))
    }

    @Test
    fun `right parentheses are parsed as RightParentheses`() {
        val expression = expressionParser.parse("))) ) ->> ) ) z)z != ->> is null is not null")
        assertThat(expression.filterIsInstance<RightParentheses>()).hasSize(7)
        assertThat(expression)
            .contains(RightParentheses(), Index.atIndex(0))
            .contains(RightParentheses(), Index.atIndex(1))
            .contains(RightParentheses(), Index.atIndex(2))
            .contains(RightParentheses(), Index.atIndex(3))
            .contains(RightParentheses(), Index.atIndex(5))
            .contains(RightParentheses(), Index.atIndex(6))
            .contains(RightParentheses(), Index.atIndex(8))
    }

    @Test
    fun `full stops by themselves fail parsing`() {
        assertThatThrownBy { expressionParser.parse("ok until we get to the full stop .") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `full stops at the start of a string fail parsing`() {
        assertThatThrownBy { expressionParser.parse(".ok until we get to the full stop") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `full stops at the end of a string fail parsing`() {
        assertThatThrownBy { expressionParser.parse("ok until we get to the full stop.") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `full stops within a string are parsed as PathReferences`() {
        val expression = expressionParser.parse("o.k unt.il we get to the full sto.p")
        assertThat(expression.filterIsInstance<PathReference>()).hasSize(8)
        assertThat(expression)
            .contains(PathReference("o.k"), Index.atIndex(0))
            .contains(PathReference("unt.il"), Index.atIndex(1))
            .contains(PathReference("sto.p"), Index.atIndex(7))
    }
}