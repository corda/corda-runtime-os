package net.corda.p2p.linkmanager.tracker

import net.corda.data.p2p.app.AppMessage
import net.corda.libs.statemanager.api.State
import net.corda.libs.statemanager.api.StateOperationGroup
import net.corda.messaging.api.records.EventLogRecord
import net.corda.p2p.linkmanager.tracker.PartitionState.Companion.stateKey
import net.corda.v5.base.exceptions.CordaRuntimeException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Instant

class PartitionStateTest {
    @Test
    fun `restartOffset set works as expected`() {
        val state = PartitionState(1, null)

        state.restartOffset = 400

        assertThat(state.restartOffset).isEqualTo(400)
    }

    @Test
    fun `lastSentOffset set works as expected`() {
        val state = PartitionState(1, null)

        state.lastSentOffset = 420

        assertThat(state.lastSentOffset).isEqualTo(420)
    }

    @Test
    fun `addMessage works as expected`() {
        val state = PartitionState(1, null)
        val message = TrackedMessageState(
            messageId = "id",
            timeStamp = mock(),
            persisted = false,
        )

        state.addMessage(message)

        assertThat(state.getTrackMessage("id")).isEqualTo(message)
    }

    @Test
    fun `untrackMessage removes message from list`() {
        val state = PartitionState(1, null)
        val message = TrackedMessageState(
            messageId = "id",
            timeStamp = mock(),
            persisted = false,
        )
        state.addMessage(message)

        state.untrackMessage(message.messageId)

        assertThat(state.getTrackMessage("id")).isNull()
    }

    @Nested
    inner class InitTests {
        @Test
        fun `init happy path`() {
            val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": 200000,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": 10000,
            "p": true
        }, {
            "id": "id2",
            "ts": 10002,
            "p": false
        }
    ]
}
            """.trimIndent()
            val state = State(
                key = "",
                value = json.toByteArray(),
                version = 100,
            )

            val partitionState = PartitionState(3, state)

            assertSoftly {
                it.assertThat(partitionState.restartOffset).isEqualTo(100000)
                it.assertThat(partitionState.lastSentOffset).isEqualTo(200000)
                it.assertThat(partitionState.getTrackMessage("id1")).isEqualTo(
                    TrackedMessageState(
                        messageId = "id1",
                        timeStamp = Instant.ofEpochMilli(10000),
                        persisted = true,
                    ),
                )
                it.assertThat(partitionState.getTrackMessage("id2")).isEqualTo(
                    TrackedMessageState(
                        messageId = "id2",
                        timeStamp = Instant.ofEpochMilli(10002),
                        persisted = false,
                    ),
                )
            }
        }

        @Test
        fun `init works find for null state`() {
            val partitionState = PartitionState(3, null)

            assertSoftly {
                it.assertThat(partitionState.restartOffset).isEqualTo(0)
                it.assertThat(partitionState.lastSentOffset).isEqualTo(0)
            }
        }

        @Nested
        inner class FailedInitTests {
            @Test
            fun `init fails with invalid JSON`() {
                val json = """{"restartOffset": 100000,"""
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with missing restartOffset`() {
                val json = """
{
    "restartOffset": "100000",
    "lastSentOffset": 200000,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": 10000,
            "p": true
        }, {
            "id": "id2",
            "ts": 10002,
            "p": false
        }
    ]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with missing lastSentOffset`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": null,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": 10000,
            "p": true
        }, {
            "id": "id2",
            "ts": 10002,
            "p": false
        }
    ]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with invalid rawTrackedMessages`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": null,
    "trackedMessages": 100
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with invalid data in rawTrackedMessages`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": null,
    "trackedMessages": [100]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with invalid message ID`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": 200000,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": 10000,
            "p": true
        }, {
            "id": 3,
            "ts": 10002,
            "p": false
        }
    ]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with invalid timeStamp`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": 200000,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": null,
            "p": true
        }, {
            "id": "id3",
            "ts": 10002,
            "p": false
        }
    ]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }

            @Test
            fun `init fails with invalid persisted`() {
                val json = """
{
    "restartOffset": 100000,
    "lastSentOffset": 200000,
    "trackedMessages": [
        {
            "id": "id1",
            "ts": null,
            "p": true
        }, {
            "id": "id3",
            "ts": 10002,
            "p": "false"
        }
    ]
}
                """.trimIndent()
                val state = State(
                    key = "",
                    value = json.toByteArray(),
                    version = 100,
                )

                assertThrows<CordaRuntimeException> {
                    PartitionState(3, state)
                }
            }
        }
    }

    @Test
    fun `addToOperationGroup add the correct state to the created group`() {
        val partitionState = PartitionState(
            1,
            null,
        )
        partitionState.restartOffset = 100
        partitionState.lastSentOffset = 400
        partitionState.addMessage(
            TrackedMessageState(
                messageId = "id1",
                timeStamp = Instant.ofEpochMilli(401),
                persisted = false,
            ),
        )
        partitionState.addMessage(
            TrackedMessageState(
                messageId = "id2",
                timeStamp = Instant.ofEpochMilli(402),
                persisted = false,
            ),
        )
        val state = argumentCaptor<State>()
        val group = mock<StateOperationGroup>() {
            on { create(state.capture()) } doReturn mock
        }

        partitionState.addToOperationGroup(group)

        assertSoftly {
            it.assertThat(state.firstValue.version).isEqualTo(State.VERSION_INITIAL_VALUE)
            it.assertThat(state.firstValue.key).isEqualTo(stateKey(1))
            val created = PartitionState(1, state.firstValue)
            it.assertThat(created.restartOffset).isEqualTo(partitionState.restartOffset)
            it.assertThat(created.lastSentOffset).isEqualTo(partitionState.lastSentOffset)
            it.assertThat(created.getTrackMessage("id1")).isEqualTo(partitionState.getTrackMessage("id1"))
            it.assertThat(created.getTrackMessage("id2")).isEqualTo(partitionState.getTrackMessage("id2"))
        }
    }

    @Test
    fun `addToOperationGroup add the correct state to the updated group`() {
        val partitionState = PartitionState(
            1,
            null,
        )
        partitionState.restartOffset = 100
        partitionState.lastSentOffset = 400
        partitionState.addMessage(
            TrackedMessageState(
                messageId = "id1",
                timeStamp = Instant.ofEpochMilli(401),
                persisted = false,
            ),
        )
        partitionState.addMessage(
            TrackedMessageState(
                messageId = "id2",
                timeStamp = Instant.ofEpochMilli(402),
                persisted = false,
            ),
        )
        val state = argumentCaptor<State>()
        val group = mock<StateOperationGroup>() {
            on { update(state.capture()) } doReturn mock
        }
        partitionState.saved()

        partitionState.addToOperationGroup(group)

        assertSoftly {
            it.assertThat(state.firstValue.version).isEqualTo(State.VERSION_INITIAL_VALUE + 1)
            it.assertThat(state.firstValue.key).isEqualTo(stateKey(1))
            val created = PartitionState(1, state.firstValue)
            it.assertThat(created.restartOffset).isEqualTo(partitionState.restartOffset)
            it.assertThat(created.lastSentOffset).isEqualTo(partitionState.lastSentOffset)
            it.assertThat(created.getTrackMessage("id1")).isEqualTo(partitionState.getTrackMessage("id1"))
            it.assertThat(created.getTrackMessage("id2")).isEqualTo(partitionState.getTrackMessage("id2"))
        }
    }

    @Test
    fun `read will update the offset`() {
        val now = Instant.ofEpochMilli(1000)
        val records = listOf(
            EventLogRecord(
                partition = 1,
                offset = 200,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 201,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 202,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 100,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
        )
        val state = PartitionState(1, null)

        state.read(now, records)

        assertThat(state.lastSentOffset).isEqualTo(202)
    }

    @Test
    fun `read will not update the offset if not more then the max`() {
        val now = Instant.ofEpochMilli(1000)
        val records = listOf(
            EventLogRecord(
                partition = 1,
                offset = 200,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 201,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 202,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 100,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
        )
        val state = PartitionState(1, null)
        state.lastSentOffset = 1000

        state.read(now, records)

        assertThat(state.lastSentOffset).isEqualTo(1000)
    }

    @Test
    fun `read will save the messages`() {
        val now = Instant.ofEpochMilli(1000)
        val records = listOf(
            EventLogRecord(
                partition = 1,
                offset = 200,
                key = "a",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 201,
                key = "b",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 202,
                key = "c",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 100,
                key = "d",
                value = mock<AppMessage>(),
                topic = "",
            ),
        )
        val state = PartitionState(1, null)
        state.lastSentOffset = 1000

        state.read(now, records)

        assertThat(state.getTrackMessage("a")).isEqualTo(
            TrackedMessageState(
                "a",
                now,
                false,
            ),
        )
    }

    @Test
    fun `sent will update the offset`() {
        val records = listOf(
            EventLogRecord(
                partition = 1,
                offset = 200,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 201,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 202,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 100,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
        )
        val state = PartitionState(1, null)

        state.sent(records)

        assertThat(state.restartOffset).isEqualTo(202)
    }

    @Test
    fun `sent will not update the offset if not more then the max`() {
        val records = listOf(
            EventLogRecord(
                partition = 1,
                offset = 200,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 201,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 202,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
            EventLogRecord(
                partition = 1,
                offset = 100,
                key = "",
                value = mock<AppMessage>(),
                topic = "",
            ),
        )
        val state = PartitionState(1, null)
        state.restartOffset = 1000

        state.sent(records)

        assertThat(state.restartOffset).isEqualTo(1000)
    }
}
