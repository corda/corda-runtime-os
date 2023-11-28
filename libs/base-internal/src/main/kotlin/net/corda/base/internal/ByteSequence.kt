package net.corda.base.internal

import net.corda.v5.base.util.ByteArrays.toHexString
import java.io.ByteArrayInputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * An abstraction of a byte array, with offset and size that does no copying of bytes unless asked to.
 *
 * The data of interest typically starts at position [offset] within the [bytes] and is [size] bytes long.
 *
 * @property offset The start position of the sequence within the byte array.
 * @property size The number of bytes this sequence represents.
 */
@Suppress("TooManyFunctions")
sealed class ByteSequence(private val _bytes: ByteArray, val offset: Int, val size: Int) : Comparable<ByteSequence> {
    /**
     * The underlying bytes.  Some implementations may choose to make a copy of the underlying [ByteArray] for
     * security reasons.  For example, [OpaqueBytes].
     */
    abstract fun getBytes(): ByteArray

    /** Returns a [ByteArrayInputStream] of the bytes. */
    fun open() = ByteArrayInputStream(_bytes, offset, size)

    /**
     * Create a sub-sequence of this sequence. A copy of the underlying array may be made, if a subclass overrides
     * [bytes] to do so, as [OpaqueBytes] does.
     *
     * @param offset The offset within this sequence to start the new sequence. Note: not the offset within the backing array.
     * @param size The size of the intended sub sequence.
     */
    @Suppress("MemberVisibilityCanBePrivate")
    fun subSequence(offset: Int, size: Int): ByteSequence {
        // Intentionally use bytes rather than _bytes, to mirror the copy-or-not behaviour of that property.
        return if (offset == 0 && size == this.size) this else OpaqueBytesSubSequence(getBytes(), this.offset + offset, size)
    }

    /**
     * Take the first n bytes of this sequence as a sub-sequence.  See [subSequence] for further semantics.
     */
    fun take(n: Int): ByteSequence = subSequence(0, n)

    /**
     * A new read-only [ByteBuffer] view of this sequence or part of it.
     * If [start] or [end] are negative then [IllegalArgumentException] is thrown, otherwise they are clamped if necessary.
     * This method cannot be used to get bytes before [offset] or after [offset]+[size], and never makes a new array.
     */
    fun slice(start: Int, end: Int): ByteBuffer {
        require(start >= 0) { "Starting index must be greater than or equal to 0" }
        require(end >= 0) { "End index must be greater or equal to 0" }
        val clampedStart = min(start, size)
        val clampedEnd = min(end, size)
        return ByteBuffer.wrap(_bytes, offset + clampedStart, max(0, clampedEnd - clampedStart)).asReadOnlyBuffer()
    }

    fun slice(start: Int): ByteBuffer = slice(start, size)

    fun slice(): ByteBuffer = slice(0, size)

    /** Write this sequence to an [OutputStream]. */
    fun writeTo(output: OutputStream) = output.write(_bytes, offset, size)

    /** Write this sequence to a [ByteBuffer]. */
    fun putTo(buffer: ByteBuffer): ByteBuffer = buffer.put(_bytes, offset, size)

    /**
     * Copy this sequence, complete with new backing array.  This can be helpful to break references to potentially
     * large backing arrays from small sub-sequences.
     */
    fun copy(): ByteSequence = OpaqueBytesSubSequence(copyBytes(), 0, size)

    /** Same as [copy] but returns just the new byte array. */
    fun copyBytes(): ByteArray = _bytes.copyOfRange(offset, offset + size)

    /**
     * Compare byte arrays byte by byte.  Arrays that are shorter are deemed less than longer arrays if all the bytes
     * of the shorter array equal those in the same position of the longer array.
     */
    override fun compareTo(other: ByteSequence): Int {
        val min = minOf(this.size, other.size)
        val thisBytes = this._bytes
        val otherBytes = other._bytes
        // Compare min bytes
        for (index in 0 until min) {
            val unsignedThis = java.lang.Byte.toUnsignedInt(thisBytes[this.offset + index])
            val unsignedOther = java.lang.Byte.toUnsignedInt(otherBytes[other.offset + index])
            if (unsignedThis != unsignedOther) {
                return Integer.signum(unsignedThis - unsignedOther)
            }
        }
        // First min bytes is the same, so now resort to size.
        return Integer.signum(this.size - other.size)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteSequence) return false
        if (this.size != other.size) return false
        return subArraysEqual(this._bytes, this.offset, this.size, other._bytes, other.offset)
    }

    private fun subArraysEqual(a: ByteArray, aOffset: Int, length: Int, b: ByteArray, bOffset: Int): Boolean {
        var bytesRemaining = length
        var aPos = aOffset
        var bPos = bOffset
        while (bytesRemaining-- > 0) {
            if (a[aPos++] != b[bPos++]) return false
        }
        return true
    }

    override fun hashCode(): Int {
        val thisBytes = _bytes
        var result = 1
        for (index in offset until (offset + size)) {
            result = 31 * result + thisBytes[index]
        }
        return result
    }

    override fun toString(): String = "[${toHexString(copyBytes())}]"
}
