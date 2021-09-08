package net.corda.messaging.emulation.topic.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.io.IOException
import java.util.concurrent.locks.ReentrantReadWriteLock

class PartitionsWriteLockTest {

    private val gotLocks = mutableListOf<Pair<String, Int>>()
    private val gotUnLocks = mutableListOf<Pair<String, Int>>()

    private fun mockPartition(nameOfTopic: String, id: Int): Partition {
        val writeLock = mock<ReentrantReadWriteLock.WriteLock> {
            on { lock() } doAnswer {
                gotLocks.add(nameOfTopic to id)
                Unit
            }
            on { unlock() } doAnswer {
                gotUnLocks.add(nameOfTopic to id)
                Unit
            }
        }
        val reentrantLock = mock<ReentrantReadWriteLock> {
            on { writeLock() } doReturn writeLock
        }
        return mock {
            on { partitionId } doReturn id
            on { topicName } doReturn nameOfTopic
            on { lock } doReturn reentrantLock
        }
    }
    private val partitionOne = mockPartition("a", 1)
    private val partitionTwo = mockPartition("a", 2)
    private val partitionThree = mockPartition("a", 3)
    private val partitionFour = mockPartition("b", 1)
    private val partitionFive = mockPartition("b", 2)
    private val partitionSix = mockPartition("b", 3)
    private val locks = PartitionsWriteLock(
        listOf(
            partitionOne,
            partitionFour,
            partitionOne,
            partitionFive,
            partitionSix,
            partitionFive,
            partitionSix,
            partitionTwo,
            partitionThree,
        )
    )

    @Test
    fun `write will lock in the correct order`() {
        locks.write {
            // Do nothing
        }

        assertThat(gotLocks).isEqualTo(
            listOf(
                "a" to 1,
                "a" to 2,
                "a" to 3,
                "b" to 1,
                "b" to 2,
                "b" to 3,
            )
        )
    }

    @Test
    fun `write will unlock in the correct order`() {
        locks.write {
            // Do nothing
        }

        assertThat(gotUnLocks).isEqualTo(
            listOf(
                "b" to 3,
                "b" to 2,
                "b" to 1,
                "a" to 3,
                "a" to 2,
                "a" to 1,
            )
        )
    }

    @Test
    @Suppress("EmptyCatchBlock")
    fun `write will unlock after Exception`() {
        try {
            locks.write {
                throw IOException("")
            }
        } catch (e: IOException) {
        }

        assertThat(gotUnLocks).isEqualTo(
            listOf(
                "b" to 3,
                "b" to 2,
                "b" to 1,
                "a" to 3,
                "a" to 2,
                "a" to 1,
            )
        )
    }

    @Test
    fun `write will run block`() {
        var a = 1
        locks.write {
            a = 2
        }

        assertThat(a).isEqualTo(2)
    }

    @Test
    fun `write will trow exception`() {
        assertThrows<IOException> {
            locks.write {
                throw IOException("")
            }
        }
    }
}
