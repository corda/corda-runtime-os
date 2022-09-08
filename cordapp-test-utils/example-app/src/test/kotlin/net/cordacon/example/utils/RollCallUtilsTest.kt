package net.cordacon.example.utils

import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RollCallUtilsTest {

    private fun MemberX500Name.toMemberInfoMock(): MemberInfo {
        val memberInfo = mock<MemberInfo>()
        whenever(memberInfo.name).thenReturn(this)
        return memberInfo
    }

    @Test
    fun `find students should use the member lookup minus teacher (self)`() {

        // Given a member lookup that knows about all the registrants including the teacher
        val teacher = MemberX500Name.parse("CN=Teach, O=Org, L=London, C=GB")
        val students = listOf("Alice", "Bob", "Charlie")
            .map {MemberX500Name.parse("CN=it, O=Org, L=London, C=GB") }
        val studentInfos = students.map { it.toMemberInfoMock() }
        val teacherInfo = teacher.toMemberInfoMock()
        val allInfo = listOf(studentInfos[0], studentInfos[2], teacherInfo, studentInfos[1])

        val memberLookup = mock<MemberLookup>()
        whenever(memberLookup.myInfo()).thenReturn(teacherInfo)
        whenever(memberLookup.lookup()).thenReturn(allInfo)

        // When we look up the students then the teacher should be removed
        assertThat(findStudents(memberLookup).map {it.name}.sorted(), `is`(students))
    }

    @Test
    fun `create script should use teacher then students while missing out empty responses`() {
        // Given some students and their responses
        val rollCallResult = listOf(
            "Alice" to "Here",
            "Bob" to "Yes",
            "Charlie" to "",
            "Charlie" to "",
            "Charlie" to "Oh! Me?"
        ).map { Pair(MemberX500Name.parse("CN=${it.first}, O=School, L=London, C=GB"), it.second) }

        // When we create a script with them plus a teacher
        val script = createScript(rollCallResult, MemberX500Name.parse("CN=Fred, O=School, L=London, C=GB"))

        // Then it should read like a film script
        assertThat(script, `is`("""
            FRED: Alice?
            ALICE: Here
            FRED: Bob?
            BOB: Yes
            FRED: Charlie?
            FRED: Charlie?
            FRED: Charlie?
            CHARLIE: Oh! Me?
            
        """.trimIndent().replace("\n", System.lineSeparator())))
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
        val script = createScript(rollCallResult, MemberX500Name.parse("CN=Fred, O=School, L=London, C=GB"))

        // Then Busch should come after Anheiser but before Bueller
        assertThat(script, `is`("""
            FRED: Anheiser?
            ANHEISER: Here
            FRED: Busch?
            BUSCH: Yes
            FRED: Bueller?
            BUELLER: Here
            
        """.trimIndent().replace("\n", System.lineSeparator())))
    }
}