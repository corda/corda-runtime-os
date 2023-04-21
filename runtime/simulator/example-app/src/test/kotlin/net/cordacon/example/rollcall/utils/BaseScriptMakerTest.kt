package net.cordacon.example.rollcall.utils

import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.junit.jupiter.api.Test

class BaseScriptMakerTest {

    @Test
    fun `should use teacher then students to create script while missing out empty responses`() {
        // Given some students and their responses
        val rollCallResult = listOf(
            "Alice" to "Here",
            "Bob" to "Yes",
            "Charlie" to "",
            "Charlie" to "",
            "Charlie" to "Oh! Me?"
        ).map { Pair(MemberX500Name.parse("CN=${it.first}, O=School, L=London, C=GB"), it.second) }

        // When we create a script with them plus a teacher
        val script = BaseScriptMaker().createScript(
            rollCallResult,
            MemberX500Name.parse("CN=Fred, O=School, L=London, C=GB")
        )

        // Then it should read like a film script
        MatcherAssert.assertThat(
            script, Matchers.`is`(
                """
            FRED: Alice?
            ALICE: Here
            FRED: Bob?
            BOB: Yes
            FRED: Charlie?
            FRED: Charlie?
            FRED: Charlie?
            CHARLIE: Oh! Me?
            
        """.trimIndent().replace("\n", System.lineSeparator())
            )
        )
    }

    @Test
    fun `script for Ferris Bueller actually has Busch before Bueller`() {
        // Given Bueller and Busch and responses
        // Given some students and their responses
        val rollCallResult = listOf(
            "Anheiser" to "Here",
            "Bueller" to "Here",
            "Busch" to "Yes"
        ).map { Pair(MemberX500Name.parse("CN=${it.first}, O=School, L=London, C=GB"), it.second) }

        // When we create a script with them plus a teacher
        val script = BaseScriptMaker().createScript(
            rollCallResult,
            MemberX500Name.parse("CN=Fred, O=School, L=London, C=GB")
        )

        // Then Busch should come after Anheiser but before Bueller
        MatcherAssert.assertThat(
            script, Matchers.`is`(
                """
            FRED: Anheiser?
            ANHEISER: Here
            FRED: Busch?
            BUSCH: Yes
            FRED: Bueller?
            BUELLER: Here
            
        """.trimIndent().replace("\n", System.lineSeparator())
            )
        )
    }
}