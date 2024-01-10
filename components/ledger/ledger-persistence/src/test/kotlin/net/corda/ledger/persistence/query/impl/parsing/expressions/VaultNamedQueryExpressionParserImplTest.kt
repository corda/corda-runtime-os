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
import net.corda.ledger.persistence.query.parsing.LeftParenthesis
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
import net.corda.ledger.persistence.query.parsing.RightParenthesis
import net.corda.ledger.persistence.query.parsing.Select
import net.corda.ledger.persistence.query.parsing.SqlType
import net.corda.ledger.persistence.query.parsing.Where
import net.corda.ledger.persistence.query.parsing.expressions.VaultNamedQueryExpressionParserImpl
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class VaultNamedQueryExpressionParserImplTest {
    private val expressionParser = VaultNamedQueryExpressionParserImpl()

    @Test
    fun `field name not in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("these ARE 123 field nAmEs")
        assertMatches(
            expression,
            listOf(
                PathReference("these"),
                PathReference("ARE"),
                Number("123"),
                PathReference("field"),
                PathReference("nAmEs")
            )
        )
    }

    @Test
    fun `field name in quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("'these' 'ARE' 123 '456' 'field' 'nAmEs.' '->>'")
        assertMatches(
            expression,
            listOf(
                PathReference("'these'"),
                PathReference("'ARE'"),
                Number("123"),
                PathReference("'456'"),
                PathReference("'field'"),
                PathReference("'nAmEs.'"),
                PathReference("'->>'")
            )
        )
    }

    @Test
    fun `field name in quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("'these ARE' 123 '456 789' 'field nAmEs.' '->> is null'")
        assertMatches(
            expression,
            listOf(
                PathReferenceWithSpaces("'these ARE'"),
                Number("123"),
                PathReferenceWithSpaces("'456 789'"),
                PathReferenceWithSpaces("'field nAmEs.'"),
                PathReferenceWithSpaces("'->> is null'")
            )
        )
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes is parsed as PathReference`() {
        val expression = expressionParser.parse("\"these\" \"ARE\" 123 \"456\" \"field\" \"nAmEs.\" \"->>\"")
        assertMatches(
            expression,
            listOf(
                PathReference("\"these\""),
                PathReference("\"ARE\""),
                Number("123"),
                PathReference("\"456\""),
                PathReference("\"field\""),
                PathReference("\"nAmEs.\""),
                PathReference("\"->>\"")
            )
        )
    }

    /**
     * Valid for columns in tables but not for JSON keys.
     */
    @Test
    fun `field name in double quotes with spaces is parsed as PathReferenceWithSpace`() {
        val expression = expressionParser.parse("\"these ARE\" 123 \"456 789\" \"field nAmEs.\" \"->> is null\"")
        assertMatches(
            expression,
            listOf(
                PathReferenceWithSpaces("\"these ARE\""),
                Number("123"),
                PathReferenceWithSpaces("\"456 789\""),
                PathReferenceWithSpaces("\"field nAmEs.\""),
                PathReferenceWithSpaces("\"->> is null\"")
            )
        )
    }

    @Test
    fun `parameter name is parsed as Parameter`() {
        val expression = expressionParser.parse(":parameter ARE 123 :another_one nAmEs != :with-dashes")
        assertMatches(
            expression,
            listOf(
                Parameter(":parameter"),
                PathReference("ARE"),
                Number("123"),
                Parameter(":another_one"),
                NotEquals(
                    listOf(PathReference("nAmEs")),
                    listOf(Parameter(":with-dashes"))
                )
            )
        )
    }

    @Test
    fun `parameter name in brackets is parsed as Parameter`() {
        assertMatches(expressionParser.parse("(:parameter)"), listOf(Parameter(":parameter")))
    }

    @Test
    fun `number with no decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1 23456 field nAmEs 78910")
        assertMatches(
            expression,
            listOf(
                Number("1"),
                Number("23456"),
                PathReference("field"),
                PathReference("nAmEs"),
                Number("78910")
            )
        )
    }

    @Test
    fun `number with decimal points is parsed as Number`() {
        val expression = expressionParser.parse("1.0 23.456 field nAmEs 78910.00000001")
        assertMatches(
            expression,
            listOf(
                Number("1.0"),
                Number("23.456"),
                PathReference("field"),
                PathReference("nAmEs"),
                Number("78910.00000001")
            )
        )
    }

    /**
     * JSON field operator
     */
    @Test
    fun `json field is parsed as JsonField`() {
        val expression = expressionParser.parse("these ARE -> field, nAmEs -> foo")
        assertMatches(
            expression,
            listOf(
                PathReference("these"),
                JsonField(
                    listOf(PathReference("ARE")),
                    listOf(PathReference("field"))
                ),
                ParameterEnd,
                JsonField(
                    listOf(PathReference("nAmEs")),
                    listOf(PathReference("foo"))
                )
            )
        )
    }

    /**
     * JSON array or object to text = "->>"
     */
    @Test
    fun `json array or object to text is parsed as JsonArrayOrObjectAsText`() {
        val expression = expressionParser.parse("these ARE ->> field, nAmEs->> foo")
        assertMatches(
            expression,
            listOf(
                PathReference("these"),
                JsonArrayOrObjectAsText(
                    listOf(PathReference("ARE")),
                    listOf(PathReference("field"))
                ),
                ParameterEnd,
                JsonArrayOrObjectAsText(
                    listOf(PathReference("nAmEs")),
                    listOf(PathReference("foo"))
                )
            )
        )
    }

    @Test
    fun `as is parsed (case insensitive) as As`() {
        val expression = expressionParser.parse("asasas AS zzz as yyy aS zasz")
        assertMatches(
            expression,
            listOf(
                As(
                    listOf(
                        As(
                            listOf(
                                As(
                                    listOf(PathReference("asasas")),
                                    listOf(PathReference("zzz"))
                                )
                            ),
                            listOf(PathReference("yyy"))
                        )
                    ),
                    listOf(PathReference("zasz"))
                )
            )
        )
    }

    @Test
    fun `from is parsed (case insensitive) as From`() {
        val expression = expressionParser.parse("fromfromfrom FROM (a fRom zfromz) AS z")
        assertMatches(
            expression,
            listOf(
                PathReference("fromfromfrom"),
                From(
                    listOf(
                        As(
                            listOf(LeftParenthesis, PathReference("a"), From(listOf(PathReference("zfromz"))), RightParenthesis),
                            listOf(PathReference("z"))
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `select is parsed (case insensitive) as Select`() {
        val expression = expressionParser.parse("selectselectselect SELECT (select a AS b), seLEcT zselectz")
        assertMatches(
            expression,
            listOf(
                PathReference("selectselectselect"),
                Select(
                    listOf(
                        LeftParenthesis,
                        Select(
                            listOf(
                                As(listOf(PathReference("a")), listOf(PathReference("b")))
                            )
                        ),
                        RightParenthesis,
                        ParameterEnd,
                        Select(listOf(PathReference("zselectz")))
                    )
                )
            )
        )
    }

    @Test
    fun `where is parsed (case insensitive) as Where`() {
        val expression = expressionParser.parse("wherewherewhere WHERE a, wheRe b, c")
        assertMatches(
            expression,
            listOf(
                PathReference("wherewherewhere"),
                Where(
                    listOf(
                        PathReference("a"),
                        ParameterEnd,
                        Where(listOf(PathReference("b"), ParameterEnd, PathReference("c")))
                    )
                )
            )
        )
    }

    @Test
    fun `and is parsed (case insensitive) as And`() {
        val expression = expressionParser.parse("andandand AND a and b ANd zandz")
        assertMatches(
            expression,
            listOf(
                And(
                    listOf(
                        And(
                            listOf(
                                And(
                                    listOf(PathReference("andandand")),
                                    listOf(PathReference("a"))
                                )
                            ),
                            listOf(PathReference("b"))
                        )
                    ),
                    listOf(PathReference("zandz"))
                )
            )
        )
    }

    @Test
    fun `or is parsed (case insensitive) as Or`() {
        val expression = expressionParser.parse("ororor OR a or b Or zorz")
        assertMatches(
            expression,
            listOf(
                Or(
                    listOf(
                        Or(
                            listOf(
                                Or(
                                    listOf(PathReference("ororor")),
                                    listOf(PathReference("a"))
                                )
                            ),
                            listOf(PathReference("b"))
                        )
                    ),
                    listOf(PathReference("zorz"))
                )
            )
        )
    }

    @Test
    fun `is null is parsed (case insensitive) as IsNull`() {
        val expression = expressionParser.parse("is nullis nullis null IS NULL, izzyNull Is NuLL, 'zis nullz'")
        assertMatches(
            expression,
            listOf(
                PathReference("is"),
                PathReference("nullis"),
                PathReference("nullis"),
                IsNull(listOf(PathReference("null"))),
                ParameterEnd,
                IsNull(listOf(PathReference("izzyNull"))),
                ParameterEnd,
                PathReferenceWithSpaces("'zis nullz'")
            )
        )
    }

    @Test
    fun `is not null is parsed (case insensitive) as IsNotNull`() {
        val expression =
            expressionParser.parse("is not nullis not nullis not null IS NOT NULL, izzyNotNull is not null, zis Is NoT NuLL")
        assertMatches(
            expression,
            listOf(
                PathReference("is"),
                PathReference("not"),
                PathReference("nullis"),
                PathReference("not"),
                PathReference("nullis"),
                PathReference("not"),
                IsNotNull(listOf(PathReference("null"))),
                ParameterEnd,
                IsNotNull(listOf(PathReference("izzyNotNull"))),
                ParameterEnd,
                IsNotNull(listOf(PathReference("zis")))
            )
        )
    }

    /**
     * !in or not in??
     */
    @Test
    fun `in is parsed (case insensitive) as In`() {
        val expression = expressionParser.parse("ininin IN () in a iN zinz")
        assertMatches(
            expression,
            listOf(
                In(
                    listOf(
                        In(
                            listOf(
                                In(
                                    listOf(PathReference("ininin")),
                                    listOf(LeftParenthesis, RightParenthesis)
                                )
                            ),
                            listOf(LeftParenthesis, PathReference("a"), RightParenthesis)
                        )
                    ),
                    listOf(LeftParenthesis, PathReference("zinz"), RightParenthesis)
                )
            )
        )
    }

    @Test
    fun `equals is parsed as Equals`() {
        val expression = expressionParser.parse("eee = a, b = z1=z2")
        assertMatches(
            expression,
            listOf(
                Equals(
                    listOf(PathReference("eee")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                Equals(
                    listOf(
                        Equals(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `not equals is parsed as NotEquals`() {
        val expression = expressionParser.parse("nnn != a, b != z1!=z2")
        assertMatches(
            expression,
            listOf(
                NotEquals(
                    listOf(PathReference("nnn")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                NotEquals(
                    listOf(
                        NotEquals(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `less than is parsed as LessThan`() {
        val expression = expressionParser.parse("lll < a, b < z1<z2")
        assertMatches(
            expression,
            listOf(
                LessThan(
                    listOf(PathReference("lll")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                LessThan(
                    listOf(
                        LessThan(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `less than or equal to is parsed as LessThanEquals`() {
        val expression = expressionParser.parse("lll <= a, b <= z1<=z2")
        assertMatches(
            expression,
            listOf(
                LessThanEquals(
                    listOf(PathReference("lll")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                LessThanEquals(
                    listOf(
                        LessThanEquals(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `greater than is parsed as GreaterThan`() {
        val expression = expressionParser.parse("ggg > a, b > z1>z2")
        assertMatches(
            expression,
            listOf(
                GreaterThan(
                    listOf(PathReference("ggg")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                GreaterThan(
                    listOf(
                        GreaterThan(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `greater than or equal to is parsed as GreaterThanEquals`() {
        val expression = expressionParser.parse("ggg >= a, b >= z1>=z2")
        assertMatches(
            expression,
            listOf(
                GreaterThanEquals(
                    listOf(PathReference("ggg")),
                    listOf(PathReference("a"))
                ),
                ParameterEnd,
                GreaterThanEquals(
                    listOf(
                        GreaterThanEquals(
                            listOf(PathReference("b")),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `json cast to is parsed as JsonCast`() {
        assertMatches(
            expressionParser.parse("a::int::int::int"),
            listOf(
                JsonCast(
                    listOf(PathReference("a")),
                    listOf(SqlType("int::int::int"))
                )
            )
        )

        assertMatches(
            expressionParser.parse("b::date"),
            listOf(
                JsonCast(
                    listOf(PathReference("b")),
                    listOf(SqlType("date"))
                )
            )
        )

//        "b::date c::string->>d::int=5 e::int>5 z::intz<=1 != f->>g is null h is not null")
//        assertThat(expression.filterIsInstance<JsonCast>()).hasSize(6)
//        assertThat(expression)
//            .contains(JsonCast("int::int::int"), Index.atIndex(0))
//            .contains(JsonCast("date"), Index.atIndex(1))
//            .contains(JsonCast("string"), Index.atIndex(2))
//            .contains(JsonCast("int"), Index.atIndex(4))
//            .contains(JsonCast("int"), Index.atIndex(7))
//            .contains(JsonCast("intz"), Index.atIndex(11))
    }

    @Test
    fun `json cast in brackets is parsed as JsonCast`() {
        val expression = expressionParser.parse("(a::int)")
        assertMatches(
            expression,
            listOf(
                LeftParenthesis,
                JsonCast(
                    listOf(PathReference("a")),
                    listOf(SqlType("int"))
                ),
                RightParenthesis
            )
        )
    }

    @Test
    fun `json key exists is parsed as JsonKeyExists`() {
        val expression = expressionParser.parse("xxx ? a ? b ? z1?z2")
        assertMatches(
            expression,
            listOf(
                JsonKeyExists(
                    listOf(
                        JsonKeyExists(
                            listOf(
                                JsonKeyExists(
                                    listOf(
                                        JsonKeyExists(
                                            listOf(PathReference("xxx")),
                                            listOf(PathReference("a"))
                                        )
                                    ),
                                    listOf(PathReference("b"))
                                )
                            ),
                            listOf(PathReference("z1"))
                        )
                    ),
                    listOf(PathReference("z2"))
                )
            )
        )
    }

    @Test
    fun `like is parsed as Like`() {
        val expression = expressionParser.parse("likelikelike LIKE foo like bar LiKe zlikez")
        assertMatches(
            expression,
            listOf(
                Like(
                    listOf(
                        Like(
                            listOf(
                                Like(
                                    listOf(PathReference("likelikelike")),
                                    listOf(PathReference("foo"))
                                )
                            ),
                            listOf(PathReference("bar"))
                        )
                    ),
                    listOf(PathReference("zlikez"))
                )
            )
        )
    }

    @Test
    fun `spaces are ignored`() {
        val expression = expressionParser.parse("  spaces1 spaces2  spaces3      spaces4 ")
        assertMatches(
            expression,
            listOf(
                PathReference("spaces1"),
                PathReference("spaces2"),
                PathReference("spaces3"),
                PathReference("spaces4")
            )
        )
    }

    @Test
    fun `tabs are ignored`() {
        val expression = expressionParser.parse("\t\tspaces1\tspaces2 \t spaces3 \t \t spaces4 \t")
        assertMatches(
            expression,
            listOf(
                PathReference("spaces1"),
                PathReference("spaces2"),
                PathReference("spaces3"),
                PathReference("spaces4")
            )
        )
    }

    @Test
    fun `new lines are ignored`() {
        val expression = expressionParser.parse("\n\nspaces1\nspaces2 \n spaces3 \n \n spaces4 \n")
        assertMatches(
            expression,
            listOf(
                PathReference("spaces1"),
                PathReference("spaces2"),
                PathReference("spaces3"),
                PathReference("spaces4")
            )
        )
    }

    @Test
    fun `commas are parsed as ParameterEnd`() {
        val expression = expressionParser.parse(",,, , op , , z,z is null")
        assertMatches(
            expression,
            listOf(
                ParameterEnd,
                ParameterEnd,
                ParameterEnd,
                ParameterEnd,
                PathReference("op"),
                ParameterEnd,
                ParameterEnd,
                PathReference("z"),
                ParameterEnd,
                IsNull(listOf(PathReference("z")))
            )
        )
    }

    @Test
    fun `parentheses are parsed as LeftParenthesis and RightParenthesis`() {
        val expression = expressionParser.parse("()")
        assertMatches(
            expression,
            listOf(
                LeftParenthesis,
                RightParenthesis
            )
        )
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
        assertMatches(
            expression,
            listOf(
                PathReference("o.k"),
                PathReference("unt.il"),
                PathReference("we"),
                PathReference("get"),
                PathReference("to"),
                PathReference("the"),
                PathReference("full"),
                PathReference("sto.p")
            )
        )
    }
}
