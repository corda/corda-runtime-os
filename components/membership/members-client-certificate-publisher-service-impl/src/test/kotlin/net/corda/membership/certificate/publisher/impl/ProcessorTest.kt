package net.corda.membership.certificate.publisher.impl

import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.p2p.mtls.MemberAllowedCertificateSubject
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoExtension.Companion.STATUS
import net.corda.membership.lib.MemberInfoExtension.Companion.TLS_CERTIFICATE_SUBJECT
import net.corda.messaging.api.records.Record
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class ProcessorTest {
    private val processor = MemberInfoProcessor()

    @Nested
    inner class OnNextTests {
        private val events: List<Record<String, PersistentMemberInfo>> = listOf(
            Record(
                "topic",
                "key1",
                createMemberInfo("subject1"),
            ),
            Record(
                "topic",
                "key2",
                createMemberInfo("subject2"),
            ),
            Record(
                "topic",
                "key3",
                createMemberInfo("subject3"),
            ),
            Record(
                "topic",
                "key4",
                null,
            ),
            Record(
                "topic",
                "key5",
                createMemberInfo(null),
            ),
            Record(
                "topic",
                "key6",
                createMemberInfo("subject", active = false),
            ),
            Record(
                "topic",
                "key7",
                createMemberInfo("subject", active = null),
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

        @Test
        fun `it returns null if status is not defined`() {
            val records = processor.onNext(events)

            assertThat(records).anySatisfy {
                it.key == "key7" && it.value == null
            }
        }
    }

    private fun createMemberInfo(
        subject: String?,
        active: Boolean? = true,
    ): PersistentMemberInfo {
        val status = if (active != null) {
            listOf(
                KeyValuePair(
                    STATUS,
                    if (active) {
                        MEMBER_STATUS_ACTIVE
                    } else {
                        MEMBER_STATUS_SUSPENDED
                    }
                )
            )
        } else {
            emptyList()
        }

        val subjectKeyPair = if (subject == null) {
            emptyList()
        } else {
            listOf(
                KeyValuePair(
                    TLS_CERTIFICATE_SUBJECT,
                    subject
                ),
            )
        }
        return mock {
            on { mgmContext } doReturn KeyValuePairList(
                status
            )
            on { memberContext } doReturn KeyValuePairList(
                subjectKeyPair
            )
        }
    }
}
