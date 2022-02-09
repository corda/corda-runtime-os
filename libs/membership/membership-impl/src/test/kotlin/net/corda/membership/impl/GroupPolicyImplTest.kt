package net.corda.membership.impl

import net.corda.membership.GroupPolicy
import net.corda.membership.impl.GroupPolicyExtension.Companion.REGISTRATION_PROTOCOL_KEY
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.mock

class GroupPolicyImplTest {

    companion object {
        const val REGISTRATION_PROTOCOL = "net.corda.membership.FakeRegistrationProtocolNameForTest"

        const val EMPTY_STRING = ""
        const val WHITESPACE_STRING = "   "
    }

    private lateinit var groupPolicy: GroupPolicy

    @BeforeEach
    fun setUp() {
        groupPolicy = mock()
    }

    @Test
    fun `Extension function returns registration protocol as defined in the group policy`() {
        setRegistrationProtocolName(REGISTRATION_PROTOCOL)
        assertEquals(REGISTRATION_PROTOCOL, groupPolicy.registrationProtocol)
    }

    @Test
    fun `Exception thrown when registration protocol is not specified`() {
        setRegistrationProtocolName(null)
        assertThrows<CordaRuntimeException> {
            groupPolicy.registrationProtocol
        }
    }

    @Test
    fun `Exception thrown when registration protocol is empty`() {
        setRegistrationProtocolName(EMPTY_STRING)
        assertThrows<CordaRuntimeException> {
            groupPolicy.registrationProtocol
        }
    }

    @Test
    fun `Exception thrown when registration protocol is whitespace`() {
        setRegistrationProtocolName(WHITESPACE_STRING)
        assertThrows<CordaRuntimeException> {
            groupPolicy.registrationProtocol
        }
    }

    fun setRegistrationProtocolName(protocol: String?) {
        groupPolicy = GroupPolicyImpl(
            if (protocol == null) {
                emptyMap()
            } else {
                mapOf(REGISTRATION_PROTOCOL_KEY to protocol)
            }
        )
    }
}