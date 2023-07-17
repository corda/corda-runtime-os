package net.corda.testing.ledger.query

import net.corda.db.hsqldb.json.HsqldbJsonExtension.JSON_SQL_TYPE
import net.corda.ledger.persistence.query.parsing.And
import net.corda.ledger.persistence.query.parsing.As
import net.corda.ledger.persistence.query.parsing.Equals
import net.corda.ledger.persistence.query.parsing.From
import net.corda.ledger.persistence.query.parsing.GreaterThan
import net.corda.ledger.persistence.query.parsing.GreaterThanEquals
import net.corda.ledger.persistence.query.parsing.HsqldbCast
import net.corda.ledger.persistence.query.parsing.HsqldbJsonArrayOrObjectAsText
import net.corda.ledger.persistence.query.parsing.HsqldbJsonField
import net.corda.ledger.persistence.query.parsing.HsqldbJsonKeyExists
import net.corda.ledger.persistence.query.parsing.In
import net.corda.ledger.persistence.query.parsing.IsNotNull
import net.corda.ledger.persistence.query.parsing.IsNull
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
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Where
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Index
import org.junit.jupiter.api.Test

class HsqldbVaultNamedQueryExpressionParserTest {

    private val expressionParser = HsqldbVaultNamedQueryExpressionParser()

    @Test
    fun `field name not in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("these ARE 123 field nAmEs != ->> is null is not null")
        assertThat(expression).filteredOn { it is PathReference }.containsExactly(
            PathReference("these"),
            PathReference("ARE"),
            PathReference("field"),
            PathReference("nAmEs")
        )
    }

    @Test
    fun `field name in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("'these' 'ARE' 123 '456' 'field' 'nAmEs.' != ->> '->>' is null is not null")
        assertThat(expression).containsExactly(
            PathReference("'these'"),
            PathReference("'ARE'"),
            Number("123"),
            PathReference("'456'"),
            PathReference("'field'"),
            PathReference("'nAmEs.'"),
            NotEquals(),
            HsqldbJsonArrayOrObjectAsText(),
            ParameterEnd(),
            PathReference("'->>'"),
            RightParentheses(),
            IsNull(),
            IsNotNull()
        )
    }

    @Test
    fun `field name in quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("'these ARE' 123 '456 789' 'field nAmEs.' != ->> '->> is null' is not null")
        assertThat(expression).filteredOn { it is PathReferenceWithSpaces }.containsExactly(
            PathReferenceWithSpaces("'these ARE'"),
            PathReferenceWithSpaces("'456 789'"),
            PathReferenceWithSpaces("'field nAmEs.'"),
            PathReferenceWithSpaces("'->> is null'")
        )
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("\"these\" \"ARE\" 123 \"456\" \"field\" \"nAmEs.\" != ->> \"->>\" is null is not null")
        assertThat(expression).filteredOn { it is PathReference }.containsExactly(
            PathReference("\"these\""),
            PathReference("\"ARE\""),
            PathReference("\"456\""),
            PathReference("\"field\""),
            PathReference("\"nAmEs.\""),
            PathReference("\"->>\"")
        )
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("\"these ARE\" 123 \"456 789\" \"field nAmEs.\" != ->> \"->> is null\" is not null")
        assertThat(expression).filteredOn { it is PathReferenceWithSpaces }.containsExactly(
            PathReferenceWithSpaces("\"these ARE\""),
            PathReferenceWithSpaces("\"456 789\""),
            PathReferenceWithSpaces("\"field nAmEs.\""),
            PathReferenceWithSpaces("\"->> is null\"")
        )
    }

    @Test
    fun `parameter name is parsed as Parameter`() {
        val expression = expressionParser.parse(":parameter ARE 123 :another_one nAmEs != :with-dashes ->> is null is not null")
        assertThat(expression).filteredOn { it is Parameter }.containsExactly(
            Parameter(":parameter"),
            Parameter(":another_one"),
            Parameter(":with-dashes")
        )
    }

    @Test
    fun `parameter name in brackets is parsed as Parameter`() {
        assertThat(expressionParser.parse("(:parameter)")).containsExactly(
            LeftParentheses(),
            Parameter(":parameter"),
            RightParentheses()
        )
    }

    @Test
    fun `number with no decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1 23456 field nAmEs 78910 != ->> is null is not null")
        assertThat(expression).filteredOn { it is Number }.containsExactly(
            Number("1"),
            Number("23456"),
            Number("78910")
        )
    }

    @Test
    fun `number with decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1.0 23.456 field nAmEs 78910.00000001 != ->> is null is not null")
        assertThat(expression).filteredOn { it is Number }.containsExactly(
            Number("1.0"),
            Number("23.456"),
            Number("78910.00000001")
        )
    }

    /**
     * JSON field operator
     */
    @Test
    fun `json field is parsed as HsqldbJsonArrayOrObjectAsText`() {
        val expression = expressionParser.parse("these ARE -> field nAmEs != ->>0 -> (a) is null is not null")
        assertThat(expression).filteredOn { it is HsqldbJsonField }.hasSize(2)
    }

    /**
     * JSON array or object to text = "->>"
     */
    @Test
    fun `json array or object to text is parsed as HsqldbJsonArrayOrObjectAsText`() {
        val expression = expressionParser.parse("these ARE ->> field nAmEs != ->> is null is not null")
        assertThat(expression)
            .contains(HsqldbJsonArrayOrObjectAsText(), Index.atIndex(1))
            .contains(HsqldbJsonArrayOrObjectAsText(), Index.atIndex(12))
            .filteredOn { it is HsqldbJsonArrayOrObjectAsText }.hasSize(2)
    }

    @Test
    fun `as is parsed (case insensitive) as As`() {
        val expression = expressionParser.parse("asasas AS ->> as aS zasz != ->> is null is not null")
        assertThat(expression)
            .contains(As(), Index.atIndex(1))
            .contains(As(), Index.atIndex(5))
            .contains(As(), Index.atIndex(6))
            .filteredOn { it is As }.hasSize(3)
    }

    @Test
    fun `from is parsed (case insensitive) as From`() {
        val expression = expressionParser.parse("fromfromfrom FROM ->> from frOM zfromz != ->> is null is not null")
        assertThat(expression)
            .contains(From(), Index.atIndex(1))
            .contains(From(), Index.atIndex(5))
            .contains(From(), Index.atIndex(6))
            .filteredOn { it is From }.hasSize(3)
    }

    @Test
    fun `select is parsed (case insensitive) as Select`() {
        val expression = expressionParser.parse("selectselectselect SELECT ->> select seLEcT zselectz != ->> is null is not null")
        assertThat(expression)
            .contains(Select(), Index.atIndex(1))
            .contains(Select(), Index.atIndex(5))
            .contains(Select(), Index.atIndex(6))
            .filteredOn { it is Select }.hasSize(3)
    }

    @Test
    fun `where is parsed (case insensitive) as Where`() {
        val expression = expressionParser.parse("wherewherewhere WHERE ->> where wheRE zwherez != ->> is null is not null")
        assertThat(expression)
            .contains(Where(), Index.atIndex(1))
            .contains(Where(), Index.atIndex(5))
            .contains(Where(), Index.atIndex(6))
            .filteredOn { it is Where }.hasSize(3)
    }

    @Test
    fun `and is parsed (case insensitive) as And`() {
        val expression = expressionParser.parse("andandand AND ->> and ANd zandz != ->> is null is not null")
        assertThat(expression)
            .contains(And(), Index.atIndex(1))
            .contains(And(), Index.atIndex(5))
            .contains(And(), Index.atIndex(6))
            .filteredOn { it is And }.hasSize(3)
    }

    @Test
    fun `or is parsed (case insensitive) as Or`() {
        val expression = expressionParser.parse("ororor OR ->> or Or zorz != ->> is null is not null")
        assertThat(expression)
            .contains(Or(), Index.atIndex(1))
            .contains(Or(), Index.atIndex(5))
            .contains(Or(), Index.atIndex(6))
            .filteredOn { it is Or }.hasSize(3)
    }

    @Test
    fun `is null is parsed (case insensitive) as IsNull`() {
        val expression = expressionParser.parse("is nullis nullis null IS NULL ->> is null Is NuLL zis nullz != ->> is not null")
        assertThat(expression)
            .contains(IsNull(), Index.atIndex(4))
            .contains(IsNull(), Index.atIndex(8))
            .contains(IsNull(), Index.atIndex(9))
            .filteredOn { it is IsNull }.hasSize(3)
    }

    @Test
    fun `is not null is parsed (case insensitive) as IsNotNull`() {
        val expression =
            expressionParser.parse("is not nullis not nullis not null IS NOT NULL ->> is not null Is NoT NuLL zis not nullz != ->> is null")
        assertThat(expression)
            .contains(IsNotNull(), Index.atIndex(7))
            .contains(IsNotNull(), Index.atIndex(11))
            .contains(IsNotNull(), Index.atIndex(12))
            .filteredOn { it is IsNotNull }.hasSize(3)
    }

    /**
     * !in or not in??
     */
    @Test
    fun `in is parsed (case insensitive) as In`() {
        val expression = expressionParser.parse("ininin IN ->> in iN zinz != ->> is null is not null")
        assertThat(expression)
            .contains(In(), Index.atIndex(1))
            .contains(In(), Index.atIndex(5))
            .contains(In(), Index.atIndex(6))
            .filteredOn { it is In }.hasSize(3)
    }

    @Test
    fun `equals is parsed as Equals`() {
        val expression = expressionParser.parse("=== = ->> = = z=z != ->> is null is not null")
        assertThat(expression)
            .contains(Equals(), Index.atIndex(0))
            .contains(Equals(), Index.atIndex(1))
            .contains(Equals(), Index.atIndex(2))
            .contains(Equals(), Index.atIndex(3))
            .contains(Equals(), Index.atIndex(7))
            .contains(Equals(), Index.atIndex(8))
            .contains(Equals(), Index.atIndex(10))
            .filteredOn { it is Equals }.hasSize(7)
    }

    @Test
    fun `not equals is parsed as NotEquals`() {
        val expression = expressionParser.parse("!=!=!= != ->> != != z!=z ->> is null is not null")
        assertThat(expression)
            .contains(NotEquals(), Index.atIndex(0))
            .contains(NotEquals(), Index.atIndex(1))
            .contains(NotEquals(), Index.atIndex(2))
            .contains(NotEquals(), Index.atIndex(3))
            .contains(NotEquals(), Index.atIndex(7))
            .contains(NotEquals(), Index.atIndex(8))
            .contains(NotEquals(), Index.atIndex(10))
            .filteredOn { it is NotEquals }.hasSize(7)
    }

    @Test
    fun `less than is parsed as LessThan`() {
        val expression = expressionParser.parse("<<< < ->> < < z<z != ->> is null is not null")
        assertThat(expression)
            .contains(LessThan(), Index.atIndex(0))
            .contains(LessThan(), Index.atIndex(1))
            .contains(LessThan(), Index.atIndex(2))
            .contains(LessThan(), Index.atIndex(3))
            .contains(LessThan(), Index.atIndex(7))
            .contains(LessThan(), Index.atIndex(8))
            .contains(LessThan(), Index.atIndex(10))
            .filteredOn { it is LessThan }.hasSize(7)
    }

    @Test
    fun `less than or equal to is parsed as LessThanEquals`() {
        val expression = expressionParser.parse("<=<=<= <= ->> <= <= z<=z != ->> is null is not null")
        assertThat(expression)
            .contains(LessThanEquals(), Index.atIndex(0))
            .contains(LessThanEquals(), Index.atIndex(1))
            .contains(LessThanEquals(), Index.atIndex(2))
            .contains(LessThanEquals(), Index.atIndex(3))
            .contains(LessThanEquals(), Index.atIndex(7))
            .contains(LessThanEquals(), Index.atIndex(8))
            .contains(LessThanEquals(), Index.atIndex(10))
            .filteredOn { it is LessThanEquals }.hasSize(7)
    }

    @Test
    fun `greater than is parsed as GreaterThan`() {
        val expression = expressionParser.parse(">>> > ->> > > z>z != ->> is null is not null")
        assertThat(expression)
            .contains(GreaterThan(), Index.atIndex(0))
            .contains(GreaterThan(), Index.atIndex(1))
            .contains(GreaterThan(), Index.atIndex(2))
            .contains(GreaterThan(), Index.atIndex(3))
            .contains(GreaterThan(), Index.atIndex(7))
            .contains(GreaterThan(), Index.atIndex(8))
            .contains(GreaterThan(), Index.atIndex(10))
            .filteredOn { it is GreaterThan }.hasSize(7)
    }

    @Test
    fun `greater than or equal to is parsed as GreaterThanEquals`() {
        val expression = expressionParser.parse(">=>=>= >= ->> >= >= z>=z != ->> is null is not null")
        assertThat(expression)
            .contains(GreaterThanEquals(), Index.atIndex(0))
            .contains(GreaterThanEquals(), Index.atIndex(1))
            .contains(GreaterThanEquals(), Index.atIndex(2))
            .contains(GreaterThanEquals(), Index.atIndex(3))
            .contains(GreaterThanEquals(), Index.atIndex(7))
            .contains(GreaterThanEquals(), Index.atIndex(8))
            .contains(GreaterThanEquals(), Index.atIndex(10))
            .filteredOn { it is GreaterThanEquals }.hasSize(7)
    }

    @Test
    fun `json cast to is parsed as HsqldbCast`() {
        val expression = expressionParser.parse("::int::int::int ::date ::string->>1 ::int=5 ::int>5 z::intz<=1 != ->> is null is not null")
        assertThat(expression).filteredOn { it is HsqldbCast }.hasSize(6)
        assertThat(expression).filteredOn { it is As }.hasSize(6)
        assertThat(expression).filteredOn { it is SqlType }.hasSize(6)

        assertThat(expression)
            .contains(HsqldbCast(), Index.atIndex(4))
            .contains(As(), Index.atIndex(5))
            .contains(SqlType("int::int::int"), Index.atIndex(6))

            .contains(HsqldbCast(), Index.atIndex(3))
            .contains(As(), Index.atIndex(8))
            .contains(SqlType("date"), Index.atIndex(9))

            .contains(HsqldbCast(), Index.atIndex(2))
            .contains(As(), Index.atIndex(11))
            .contains(SqlType("string"), Index.atIndex(12))

            .contains(HsqldbCast(), Index.atIndex(0))
            .contains(As(), Index.atIndex(17))
            .contains(SqlType("int"), Index.atIndex(18))

            .contains(HsqldbCast(), Index.atIndex(21))
            .contains(As(), Index.atIndex(23))
            .contains(SqlType("int"), Index.atIndex(24))

            .contains(HsqldbCast(), Index.atIndex(28))
            .contains(As(), Index.atIndex(30))
            .contains(SqlType("intz"), Index.atIndex(31))
    }

    @Test
    fun `json cast in brackets is parsed as HsqldbCast`() {
        assertThat(expressionParser.parse("(::int)")).containsExactly(
            LeftParentheses(),
            HsqldbCast(),
            As(),
            SqlType("int"),
            RightParentheses(),
            RightParentheses()
        )
    }

    @Test
    fun `json key exist is parsed as HsqldbJsonKeyExists`() {
        val expression = expressionParser.parse("??? ? ->> ? ? z?z ->> is null is not null")
        assertThat(expression)
            .contains(HsqldbJsonKeyExists(), Index.atIndex(1))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(2))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(3))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(5))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(6))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(7))
            .contains(HsqldbJsonKeyExists(), Index.atIndex(8))
            .filteredOn { it is HsqldbJsonKeyExists }.hasSize(7)
    }

    @Test
    fun `like is parsed as Like`() {
        val expression = expressionParser.parse("likelikelike LIKE ->> like LiKe zlikez != ->> is null is not null")
        assertThat(expression).filteredOn { it is Like }.hasSize(3)
        assertThat(expression)
            .contains(Like(), Index.atIndex(1))
            .contains(Like(), Index.atIndex(5))
            .contains(Like(), Index.atIndex(6))
    }

    @Test
    fun `spaces are ignored`() {
        val expression = expressionParser.parse("  spaces spaces  spaces      spaces ")
        assertThat(expression)
            .hasSize(4)
            .filteredOn { it is PathReference }.hasSize(4)
    }

    @Test
    fun `tabs are ignored`() {
        val expression = expressionParser.parse("\t\tspaces\tspaces \t spaces \t \t spaces \t")
        assertThat(expression)
            .hasSize(4)
            .filteredOn { it is PathReference }.hasSize(4)
    }

    @Test
    fun `new lines are ignored`() {
        val expression = expressionParser.parse("\n\nspaces\nspaces \n spaces \n \n spaces \n")
        assertThat(expression)
            .hasSize(4)
            .filteredOn { it is PathReference }.hasSize(4)
    }

    @Test
    fun `commas are parsed as ParameterEnds`() {
        val expression = expressionParser.parse(",,, , ->> , , z,z != ->> is null is not null")
        assertThat(expression).filteredOn { it is ParameterEnd }.hasSizeGreaterThanOrEqualTo(7)
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
        assertThat(expression)
            .contains(LeftParentheses(), Index.atIndex(0))
            .contains(LeftParentheses(), Index.atIndex(1))
            .contains(LeftParentheses(), Index.atIndex(2))
            .contains(LeftParentheses(), Index.atIndex(3))
            .contains(LeftParentheses(), Index.atIndex(6))
            .contains(LeftParentheses(), Index.atIndex(7))
            .contains(LeftParentheses(), Index.atIndex(9))
            .filteredOn { it is LeftParentheses }.hasSize(7)
    }

    @Test
    fun `right parentheses are parsed as RightParentheses`() {
        val expression = expressionParser.parse("))) ) ->> ) ) z)z != ->> is null is not null")
        assertThat(expression)
            .contains(RightParentheses(), Index.atIndex(1))
            .contains(RightParentheses(), Index.atIndex(2))
            .contains(RightParentheses(), Index.atIndex(3))
            .contains(RightParentheses(), Index.atIndex(4))
            .contains(RightParentheses(), Index.atIndex(6))
            .contains(RightParentheses(), Index.atIndex(7))
            .contains(RightParentheses(), Index.atIndex(8))
            .contains(RightParentheses(), Index.atIndex(10))
            .contains(RightParentheses(), Index.atIndex(15))
            .filteredOn { it is RightParentheses }.hasSize(9)
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

    @Test
    fun `with json fields inside brackets`() {
        assertThat(expressionParser.parse("WHERE :value = (a)->>(b)")).containsExactly(
            Where(),
            Parameter(":value"),
            Equals(),
            HsqldbJsonArrayOrObjectAsText(),
            HsqldbCast(),
            PathReference("a"),
            As(),
            SqlType(JSON_SQL_TYPE),
            RightParentheses(),
            ParameterEnd(),
            LeftParentheses(),
            PathReferenceWithSpaces("'b'"),
            RightParentheses(),
            RightParentheses()
        )
    }

    @Test
    fun `cast json field to int`() {
        assertThat(expressionParser.parse("WHERE a->b->>c::int = 0")).containsExactly(
            Where(),
            HsqldbCast(),
            HsqldbJsonArrayOrObjectAsText(),
            HsqldbJsonField(),
            HsqldbCast(), PathReference("a"), As(), SqlType(JSON_SQL_TYPE), RightParentheses(),
            ParameterEnd(),
            PathReferenceWithSpaces("'b'"), RightParentheses(),
            ParameterEnd(),
            PathReferenceWithSpaces("'c'"), RightParentheses(),
            As(), SqlType("int"), RightParentheses(),
            Equals(),
            Number("0")
        )
    }

    @Test
    fun `json array index as parameter`() {
        assertThat(expressionParser.parse("WHERE (a)->(b)->>(:index) = :value")).containsExactly(
            Where(),
            HsqldbJsonArrayOrObjectAsText(),
            HsqldbJsonField(),
            HsqldbCast(), PathReference("a"), As(), SqlType(JSON_SQL_TYPE), RightParentheses(),
            ParameterEnd(),
            LeftParentheses(), PathReferenceWithSpaces("'b'"), RightParentheses(),
            RightParentheses(),
            ParameterEnd(),
            LeftParentheses(), Parameter(":index"), RightParentheses(),
            RightParentheses(),
            Equals(),
            Parameter(":value")
        )
    }

    @Test
    fun `cast parameter as int`() {
        assertThat(expressionParser.parse(":index::int")).containsExactly(
            HsqldbCast(), Parameter(":index"), As(), SqlType("int"), RightParentheses()
        )

        assertThat(expressionParser.parse("((:index)::int)")).containsExactly(
            LeftParentheses(),
            HsqldbCast(), Parameter(":index"), As(), SqlType("int"), RightParentheses(),
            RightParentheses()
        )

        assertThat(expressionParser.parse("(a)->>(:index::int)")).containsExactly(
            HsqldbJsonArrayOrObjectAsText(),
            HsqldbCast(), PathReference("a"), As(), SqlType(JSON_SQL_TYPE), RightParentheses(),
            ParameterEnd(),
            LeftParentheses(),
            HsqldbCast(), Parameter(":index"), As(), SqlType("int"), RightParentheses(),
            RightParentheses(),
            RightParentheses()
        )

        assertThat(expressionParser.parse("(a)->>((:index)::int)")).containsExactly(
            HsqldbJsonArrayOrObjectAsText(),
            HsqldbCast(), PathReference("a"), As(), SqlType(JSON_SQL_TYPE), RightParentheses(),
            ParameterEnd(),
            LeftParentheses(),
            HsqldbCast(), Parameter(":index"), As(), SqlType("int"), RightParentheses(),
            RightParentheses(),
            RightParentheses()
        )
    }
}
