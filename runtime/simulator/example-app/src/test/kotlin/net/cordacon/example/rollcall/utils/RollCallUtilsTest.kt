package net.cordacon.example.rollcall.utils

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
    fun `find students should use and sort the member lookup for all with same org minus teacher (self)`() {

        // Given a member lookup that knows about all the registrants including the teacher
        val teacher = MemberX500Name.parse("CN=Teach, O=Org, L=London, C=GB")
        val students = listOf("Alice", "Bob", "Charlie").map {
            MemberX500Name.parse("CN=it, O=Org, L=London, C=GB")
        }
        val parents = listOf("Diana", "Eric").map {
            MemberX500Name.parse("CN=$it, O=OtherOrg, L=London, C=GB")
        }
        val studentInfos = students.map { it.toMemberInfoMock() }
        val teacherInfo = teacher.toMemberInfoMock()
        val parentInfos = parents.map { it.toMemberInfoMock() }
        val allInfoInNoParticularOrder = listOf(
            studentInfos[0],
            parentInfos[0],
            studentInfos[2],
            teacherInfo,
            studentInfos[1],
            parentInfos[1]
        )

        val memberLookup = mock<MemberLookup>()
        whenever(memberLookup.myInfo()).thenReturn(teacherInfo)
        whenever(memberLookup.lookup()).thenReturn(allInfoInNoParticularOrder)

        // When we look up the students then the teacher should be removed
        assertThat(findStudents(memberLookup).map {it.name}, `is`(students))
    }
}