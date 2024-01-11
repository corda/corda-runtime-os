package net.corda.membership.impl.registration

import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.slf4j.Logger
import java.util.UUID

class RegistrationLoggerTest {

    private val mockLogger: Logger = mock()
    private val testMemberX500Name = MemberX500Name.parse("O=Alice, L=London, C=GB")
    private val testMgmX500Name = MemberX500Name.parse("O=MGM, L=London, C=GB")
    private val testRegistrationId = UUID(0, 1).toString()
    private val testGroupId = UUID(0, 2).toString()
    private val testMemberHoldingIdentity = HoldingIdentity(testMemberX500Name, testGroupId)
    private val testMgmHoldingIdentity = HoldingIdentity(testMgmX500Name, testGroupId)

    // object under test
    private val testObj: RegistrationLogger = RegistrationLogger(mockLogger)

    @Test
    fun `registration logger logs configured properties at info level`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this info!"

        testObj.info(logLine)
        verify(mockLogger).info(getExpectedLogString(logLine, expected))
    }

    @Test
    fun `registration logger logs configured properties at info level with exception`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this info!"
        val exception: Throwable = mock()

        testObj.info(logLine, exception)
        verify(mockLogger).info(getExpectedLogString(logLine, expected), exception)
    }

    @Test
    fun `registration logger logs configured properties at warn level`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this warning!"

        testObj.warn(logLine)
        verify(mockLogger).warn(getExpectedLogString(logLine, expected))
    }

    @Test
    fun `registration logger logs configured properties at warn level with exception`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this warning!"
        val exception: Throwable = mock()

        testObj.warn(logLine, exception)
        verify(mockLogger).warn(getExpectedLogString(logLine, expected), exception)
    }

    @Test
    fun `registration logger logs configured properties at error level`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this error!"

        testObj.error(logLine)
        verify(mockLogger).error(getExpectedLogString(logLine, expected))
    }

    @Test
    fun `registration logger logs configured properties at error level with exception`() {
        testObj
            .setRegistrationId(testRegistrationId)
            .setMember(testMemberHoldingIdentity)
            .setMgm(testMgmHoldingIdentity)

        val expected = mapOf(
            "registration-id" to testRegistrationId,
            "member-holding-id" to testMemberHoldingIdentity.shortHash.value,
            "member-name" to testMemberHoldingIdentity.x500Name.toString(),
            "group-id" to testGroupId,
            "mgm-holding-id" to testMgmHoldingIdentity.shortHash.value,
            "mgm-name" to testMgmHoldingIdentity.x500Name.toString(),
        )

        val logLine = "log this error!"
        val exception: Throwable = mock()

        testObj.error(logLine, exception)
        verify(mockLogger).error(getExpectedLogString(logLine, expected), exception)
    }

    private fun getExpectedLogString(logInput: String, expected: Map<String, String>): String {
        return "$logInput $expected"
    }
}
