package net.corda.membership.certificate.publisher.impl

import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.membership.lib.MemberInfoFactory
import net.corda.messaging.api.records.Record
import net.corda.v5.membership.MemberContext
import net.corda.v5.membership.MemberInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

internal class ProcessorTest {
    private val memberInfoFactory = mock<MemberInfoFactory>()
    private val processor = MemberInfoProcessor(memberInfoFactory)

    @Nested
    inner class OnNextTests {
        private val events: List<Record<String, PersistentMemberInfo>> = listOf(
            Record(
                "topic",
                "key1",
                mockPersistentMemberInfo("subject1"),
            ),
            Record(
                "topic",
                "key2",
                mockPersistentMemberInfo("subject2"),
            ),
            Record(
                "topic",
                "key3",
                mockPersistentMemberInfo("subject3"),
            ),
            Record(
                "topic",
                "key4",
                null,
            ),
            Record(
                "topic",
                "key5",
                mockPersistentMemberInfo(null),
            ),
            Record(
                "topic",
                "key6",
                mockPersistentMemberInfo("subject", active = false),
            ),
        )

        @Test
        fun `it returns a record for each event`() {
            val records = processor.onNext(events)

            assertThat(records).hasSize(events.size)
        }

        @Test
        fun `it returns the subjects when possible`() {
            val records = processor.onNext(events)

            val keyToSubject = records.map {
                it.key to (it.value as? MemberAllowedCertificateSubject)?.subject
            }.filter {
                it.second != null
            }.toMap()
            assertThat(keyToSubject).containsExactlyEntriesOf(
                mapOf(
                    "key1" to "subject1",
                    "key2" to "subject2",
                    "key3" to "subject3",
                )
            )
        }

        @Test
        fun `it returns null for null value`() {
            val records = processor.onNext(events)

            assertThat(records).anySatisfy {
                it.key == "key4" && it.value == null
            }
        }

        @Test
        fun `it returns null for null subject`() {
            val records = processor.onNext(events)

            assertThat(records).anySatisfy {
                it.key == "key5" && it.value == null
            }
        }

        @Test
        fun `it returns null for inactive member`() {
            val records = processor.onNext(events)

            assertThat(records).anySatisfy {
                it.key == "key6" && it.value == null
            }
        }
    }

    private fun createMemberInfo(
        subject: String?,
        active: Boolean,
    ): MemberInfo {
        val memberContext = mock<MemberContext> {
            on { parseOrNull(TLS_CERTIFICATE_SUBJECT, String::class.java) } doReturn subject
        }
        return mock {
            on { memberProvidedContext } doReturn memberContext
            on { isActive } doReturn active
        }
    }

    private fun mockPersistentMemberInfo(
        subject: String?,
        active: Boolean = true,
    ): PersistentMemberInfo {
        val persistentMemberInfo = mock<PersistentMemberInfo>()
        val memberInfo = createMemberInfo(subject, active)
        whenever(
            memberInfoFactory.createMemberInfo(persistentMemberInfo)
        ).thenReturn(memberInfo)
        return persistentMemberInfo
    }
}
