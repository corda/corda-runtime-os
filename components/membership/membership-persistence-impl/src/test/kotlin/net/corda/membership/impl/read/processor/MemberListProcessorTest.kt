package net.corda.membership.impl.read.processor

import net.corda.data.membership.SignedMemberInfo
import net.corda.membership.impl.read.cache.MemberListCache
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class MemberListProcessorTest {
    private lateinit var memberListProcessor: MemberListProcessor

    private val memberListCache: MemberListCache = mock()

    @BeforeEach
    fun setUp() {
        memberListProcessor = MemberListProcessor(memberListCache)
    }

    @Test
    fun `Key class is String`() {
        Assertions.assertEquals(String::class.java, memberListProcessor.keyClass)
    }

    @Test
    fun `Value class is SignedMemberInfo`() {
        Assertions.assertEquals(SignedMemberInfo::class.java, memberListProcessor.valueClass)
    }
}