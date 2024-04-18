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
    private val offsets = TreeMap<Long, AtomicBoolean>()
    private val preCommitted = mutableSetOf<Long>()

    fun assigned() {
        offsets.clear()
        preCommitted.clear()
    }

    fun recordPolledOffset(offset: Long) {
        offsets.putIfAbsent(offset, AtomicBoolean(false))
    }

    fun recordOffsetPreCommit(offset: Long) {
        val flag = offsets[offset] ?: return
        if(flag.get()) return
        flag.set(true)
        preCommitted.add(offset)
    }

    // null means no change and thus nothing to commit
    // otherwise returns the highest offset for which recordOffsetPreCommit() had been called for which there are
    // no lower offsets for which recordPolledOffset() was called and recordOffsetCommitted() was not called
    fun getCommittableOffset(): Long? {
        var highest: Long? = null
        offsets.entries.forEach {
            if(it.value.get()) {
                highest = it.key
            } else {
                return highest
            }
        }
        return highest
    }

    // Confirm success committing a value previous returned from getCommittableOffset()
    fun commit() {
        preCommitted.clear()
        val entryIterator = offsets.entries.iterator()
        while(entryIterator.hasNext()) {
            if(entryIterator.next().value.get()) {
                entryIterator.remove()
            } else break
        }
    }

    fun rollback() {
        for (offset in preCommitted) {
            val flag = offsets[offset] ?: continue
            flag.set(false)
        }
        preCommitted.clear()
    }

    fun getLowestUncommittedOffset(): Long? {
        return offsets.firstEntry()?.key
    }
}