package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AuthenticatedMessage
import net.corda.data.p2p.app.AuthenticatedMessageHeader
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.p2p.linkmanager.sessions.SessionManager
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.fromState
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Instant
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

class PartitionStateTest {
    @Nested
    inner class FromState {
        @Test
        fun `it reads the state correctly`() {
            val json = """
{
    "processRecordsFromOffset": 300,
    "readRecordsFromOffset": 204,
    "messages": {
        "group-2": {
            "CN=Alice, O=Alice Corp, L=LDN, C=GB": {
                "CN=Bob, O=Bob Corp, L=LDN, C=GB": {
                    "id-2": {
                        "id": "id-2",
                        "ts": 10.231
                    }
                }
            }
        },
        "group": {
            "CN=Alice, O=Alice Corp, L=LDN, C=GB": {
                "CN=Carol, O=Carol Corp, L=LDN, C=GB": {
                    "id-3": {
                        "id": "id-3",
                        "ts": 10.231
                    }
                },
                "CN=Bob, O=Bob Corp, L=LDN, C=GB": {
                    "id-1": {
                        "id": "id-1",
                        "ts": 10.231
                    }
                }
            }
        }
    },
    "version": 12
}
    """
            val state =
                State(
                    key = stateKey(1),
                    value = json.toByteArray(),
                )

            val partitionState = fromState(3, state)

            assertSoftly {
                it.assertThat(partitionState.readRecordsFromOffset).isEqualTo(204)
                it.assertThat(partitionState.processRecordsFromOffset).isEqualTo(300)
                it.assertThat(partitionState.counterpartiesToMessages()).hasSize(3)
            }
        }

        @Test
        fun `it reads null state correctly`() {
            val partitionState = fromState(3, null)

            assertSoftly {
                it.assertThat(partitionState.readRecordsFromOffset).isEqualTo(-1)
                it.assertThat(partitionState.processRecordsFromOffset).isEqualTo(-1)
                it.assertThat(partitionState.counterpartiesToMessages()).hasSize(0)
            }
        }

        @Test
        fun `it throws expcetion for invalid JSON`() {
            val json = "{"
            val state =
                State(
                    key = stateKey(1),
                    value = json.toByteArray(),
                )

            assertThatThrownBy {
                fromState(3, state)
            }.isInstanceOf(CordaRuntimeException::class.java)
        }
    }

    @Test
    fun `readRecordsFromOffset can be updated`() {
        val state = PartitionState(1)

        state.readRecordsFromOffset = 132

        assertThat(state.readRecordsFromOffset).isEqualTo(132)
    }

    @Test
    fun `processRecordsFromOffset can be updated`() {
        val state = PartitionState(1)

        state.processRecordsFromOffset = 132

        assertThat(state.processRecordsFromOffset).isEqualTo(132)
    }

    @Test
    fun `counterpartiesToMessages returns all the messages`() {
        val state = PartitionState(1)
        state.read(
            Instant.ofEpochMilli(100),
            (1..4).flatMap { i ->
                val source = "CN=Alice-$i, O=Alice Corp, L=LDN, C=GB"
                (1..3).flatMap { j ->
                    val target = "CN=Bob-$j, O=Bob Corp, L=LDN, C=GB"
                    (1..2).map { k ->
                        MessageRecord(
                            offset = i * 1000L + j * 100 + k,
                            partition = 1,
                            message = createMessage(
                                id = "id-$i-$j-$k",
                                group = "group-$i",
                                from = source,
                                to = target,
                            ),
                        )
                    }
                }
            },
        )

        val counterpartiesToMessages = state.counterpartiesToMessages()

        assertThat(counterpartiesToMessages)
            .anyMatch { (counterParties, messages) ->
                counterParties == SessionManager.Counterparties(
                    ourId = HoldingIdentity(
                        groupId = "group-1",
                        x500Name = MemberX500Name.parse("CN=Alice-1, O=Alice Corp, L=LDN, C=GB"),
                    ),
                    counterpartyId = HoldingIdentity(
                        groupId = "group-1",
                        x500Name = MemberX500Name.parse("CN=Bob-2, O=Bob Corp, L=LDN, C=GB"),
                    ),
                ) && messages.contains(
                    TrackedMessageState(messageId = "id-1-2-1", timeStamp = Instant.ofEpochMilli(100)),
                ) && messages.contains(
                    TrackedMessageState(messageId = "id-1-2-2", timeStamp = Instant.ofEpochMilli(100)),
                ) && messages.size == 2
            }
            .hasSize(12)
    }

    @Nested
    inner class AddToOperationGroupTest {
        @Test
        fun `it create the correct JSON`() {
            val state = PartitionState(1)
            state.read(
                Instant.ofEpochMilli(100),
                (1..4).flatMap { i ->
                    val source = "CN=Alice-$i, O=Alice Corp, L=LDN, C=GB"
                    (1..3).flatMap { j ->
                        val target = "CN=Bob-$j, O=Bob Corp, L=LDN, C=GB"
                        (1..2).map { k ->
                            MessageRecord(
                                offset = i * 1000L + j * 100 + k,
                                partition = 1,
                                message = createMessage(
                                    id = "id-$i-$j-$k",
                                    group = "group-$i",
                                    from = source,
                                    to = target,
                                ),
                            )
                        }
                    }
                },
            )
            state.readRecordsFromOffset = 2003
            state.processRecordsFromOffset = 2001
            val captureState = argumentCaptor<State>()
            val group = mock<StateOperationGroup> {
                on { create(captureState.capture()) } doReturn mock
            }

            state.addToOperationGroup(group)

            val savedState = fromState(1, captureState.firstValue)

            assertSoftly { softly ->
                softly.assertThat(savedState.readRecordsFromOffset).isEqualTo(state.readRecordsFromOffset)
                softly.assertThat(savedState.processRecordsFromOffset).isEqualTo(state.processRecordsFromOffset)
                softly.assertThat(
                    savedState.counterpartiesToMessages()
                        .toMap()
                        .mapValues {
                            it.value.toSet()
                        },
                )
                    .containsAllEntriesOf(
                        state.counterpartiesToMessages()
                            .toMap()
                            .mapValues {
                                it.value.toSet()
                            },
                    )
            }
        }

        @Test
        fun `it create a new state when needed`() {
            val state = PartitionState(1)
            val group = mock<StateOperationGroup> {
                on { create(any<State>()) } doReturn mock
            }

            state.addToOperationGroup(group)

            verify(group).create(any<State>())
        }

        @Test
        fun `it update a state when needed`() {
            val state = PartitionState(1)
            state.saved()
            val group = mock<StateOperationGroup> {
                on { update(any<State>()) } doReturn mock
            }

            state.addToOperationGroup(group)

            verify(group).update(any<State>())
        }
    }

    @Test
    fun `read return only the new messages`() {
        val state = PartitionState(1)
        state.read(
            Instant.ofEpochMilli(100),
            listOf(
                MessageRecord(
                    offset = 3,
                    partition = 3,
                    message = createMessage(
                        id = "id-3",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
                MessageRecord(
                    offset = 4,
                    partition = 4,
                    message = createMessage(
                        id = "id-4",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
            ),
        )

        val newMessages = state.read(
            Instant.ofEpochMilli(100),
            listOf(
                MessageRecord(
                    offset = 1,
                    partition = 1,
                    message = createMessage(
                        id = "id-1",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
                MessageRecord(
                    offset = 2,
                    partition = 2,
                    message = createMessage(
                        id = "id-2",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
                MessageRecord(
                    offset = 3,
                    partition = 3,
                    message = createMessage(
                        id = "id-3",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
                MessageRecord(
                    offset = 4,
                    partition = 4,
                    message = createMessage(
                        id = "id-4",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
            ),
        )

        assertThat(newMessages.map { it.offset })
            .contains(
                1,
                2,
            )
            .hasSize(2)
    }

    @Test
    fun `forget will remove the messages`() {
        val state = PartitionState(1)
        val message = createMessage(
            id = "id-3",
            group = "group-1",
            from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
            to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
        )
        state.read(
            Instant.ofEpochMilli(100),
            listOf(
                MessageRecord(
                    offset = 3,
                    partition = 3,
                    message = message,
                ),
                MessageRecord(
                    offset = 4,
                    partition = 4,
                    message = createMessage(
                        id = "id-4",
                        group = "group-1",
                        from = "CN=Alice, O=Alice Corp, L=LDN, C=GB",
                        to = "CN=Bob, O=Bob Corp, L=LDN, C=GB",
                    ),
                ),
            ),
        )

        state.forget(message)

        assertThat(
            state.counterpartiesToMessages()
                .flatMap {
                    it.second
                },
        ).hasSize(1)
    }

    private fun createMessage(
        id: String,
        group: String,
        from: String,
        to: String,
    ): AuthenticatedMessage {
        val headers = mock<AuthenticatedMessageHeader> {
            on { messageId } doReturn id
            on { source } doReturn AvroHoldingIdentity(
                from,
                group,
            )
            on { destination } doReturn AvroHoldingIdentity(
                to,
                group,
            )
        }

        return mock {
            on { header } doReturn headers
        }
    }
}
