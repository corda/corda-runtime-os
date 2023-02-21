package net.corda.chunking.db.impl.validation

import net.corda.libs.cpiupload.ValidationException
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.membership.lib.grouppolicy.GroupPolicyParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class ValidateGroupIdTest {

    @Test
    fun `fails when group id is empty`() {
        val mockMetadata = mock<CpiMetadata> { on { groupPolicy }.doReturn("{}") }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }

        assertThrows<ValidationException> { cpi.validateAndGetGroupId("") { "" } }
        assertThrows<ValidationException> { cpi.validateAndGetGroupId("") { "   " } }
    }

    @Test
    fun `succeeds with group id`() {
        val expectedGroupId = "xxxxx"
        val json = """{ "groupId": "$expectedGroupId" }"""
        val mockMetadata = mock<CpiMetadata> { on { groupPolicy }.doReturn(json ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }
        assertThat(cpi.validateAndGetGroupId("", GroupPolicyParser::groupIdFromJson)).isEqualTo(expectedGroupId)
    }

    @Test
    fun `fails with no group id in json`() {
        val emptyJson = """{ }"""
        val mockMetadata = mock<CpiMetadata> { on { groupPolicy }.doReturn(emptyJson ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }
        assertThrows<ValidationException> {cpi.validateAndGetGroupId("", GroupPolicyParser::groupIdFromJson)}
    }

    @Test
    fun `fails with empty group id in json`() {
        val emptyJson = """{ "groupId": "" }"""
        val mockMetadata = mock<CpiMetadata> { on { groupPolicy }.doReturn(emptyJson ) }
        val cpi = mock<Cpi> { on { metadata }.doReturn(mockMetadata) }
        assertThrows<ValidationException> {cpi.validateAndGetGroupId("", GroupPolicyParser::groupIdFromJson)}
    }
}
