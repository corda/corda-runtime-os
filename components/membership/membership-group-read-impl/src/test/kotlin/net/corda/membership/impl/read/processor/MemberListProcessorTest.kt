package net.corda.membership.impl.read.processor

import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.impl.read.cache.MemberListCache
import net.corda.membership.impl.read.cache.MembershipGroupReadCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MemberListProcessorTest {
    private lateinit var memberListProcessor: MemberListProcessor

    private val membershipGroupReadCache: MembershipGroupReadCache = mock()

    @BeforeEach
    fun setUp() {
        memberListProcessor = MemberListProcessor(membershipGroupReadCache)
    }

    @Test
    fun `Key class is String`() {
        assertEquals(String::class.java, memberListProcessor.keyClass)
    }

    @Test
    fun `Value class is SignedMemberInfo`() {
        assertEquals(SignedMemberInfo::class.java, memberListProcessor.valueClass)
    }
}