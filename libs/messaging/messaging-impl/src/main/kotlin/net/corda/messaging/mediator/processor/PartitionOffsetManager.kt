package net.corda.messaging.mediator.processor

import java.util.TreeMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Expected to be used like:
 *
 * val messages = consumer.poll()
 * messages.forEach { pom.recordPolledOffset(it.offset) }
 * try {
 *   // Any of these might throw
 *   messages.forEach { process(it) }
 *   messages.forEach { pom.recordOffsetPreCommit(it.offset) }
 *   consumer.commitOffset(pom.getCommittableOffset())
 *   pom.commit()
 * } catch(e: Exception) {
 *   pom.rollback()
 * }
 */
class PartitionOffsetManager {
    private class State : AtomicBoolean(false) {
        private var meta: MutableSet<String>? = null
        fun addTag(tag: String) {
            if (meta == null) {
                meta = mutableSetOf(tag)
            } else {
                meta!! += tag
            }
        }

        override fun toString(): String {
            return "State(${this.get()}, meta=${meta ?: "()"})"
        }
    }

    private val offsets = TreeMap<Long, State>()
    private val preCommitted = mutableSetOf<Long>()
    private var polledCount = 0L
    private var committedCount = 0L

    @Synchronized
    fun assigned() {
        offsets.clear()
        preCommitted.clear()
    }

    @Synchronized
    fun recordPolledOffset(offset: Long) {
        if (offsets.putIfAbsent(offset, State()) == null) {
            polledCount++
        }
    }

    @Synchronized
    fun recordOffsetPreCommit(offset: Long) {
        val flag = offsets[offset] ?: return
        if (flag.get()) return
        flag.set(true)
        preCommitted.add(offset)
    }

    @Synchronized
    fun recordOffsetTag(offset: Long, tag: String) {
        val state = offsets[offset] ?: return
        state.addTag(tag)
    }

    // null means no change and thus nothing to commit
    // otherwise returns the highest offset for which recordOffsetPreCommit() had been called for which there are
    // no lower offsets for which recordPolledOffset() was called and recordOffsetCommitted() was not called
    @Synchronized
    fun getCommittableOffset(): Long? {
        var highest: Long? = null
        offsets.entries.forEach {
            if (it.value.get()) {
                highest = it.key
            } else {
                return highest
            }
        }
        return highest
    }

    // Confirm success committing a value previous returned from getCommittableOffset()
    @Synchronized
    fun commit() {
        preCommitted.clear()
        val entryIterator = offsets.entries.iterator()
        while (entryIterator.hasNext()) {
            if (entryIterator.next().value.get()) {
                committedCount++
                entryIterator.remove()
            } else break
        }
    }

    @Synchronized
    fun rollback() {
        for (offset in preCommitted) {
            val flag = offsets[offset] ?: continue
            flag.set(false)
        }
        preCommitted.clear()
    }

    @Synchronized
    fun getLowestUncommittedOffset(): Long? {
        return offsets.firstEntry()?.key
    }

    @Synchronized
    fun getHighestUncommittedOffset(): Long? {
        return offsets.lastEntry()?.key
    }

    @Synchronized
    override fun toString(): String {
        return "${this.javaClass.simpleName}(size=${offsets.size}, polled=$polledCount, preCommited=${preCommitted.size}, committed=$committedCount, lowestOffset=${offsets.firstEntry()}, highestOffset=${getHighestUncommittedOffset()}, canCommitOffset=${getCommittableOffset()})"
    }
}