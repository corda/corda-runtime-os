package net.corda.v5.base.types;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.copyOfRange;
import static net.corda.v5.base.types.ByteArrays.requireNotNull;
import static net.corda.v5.base.types.ByteArrays.toHexString;

/**
 * An abstraction of a byte array, with offset and size that does no copying of bytes unless asked to.
 * <p>
 * The data of interest typically starts at position {@code offset} within the {@code bytes} and is {@code size} bytes long.
 */
@SuppressWarnings("TooManyFunctions")
public abstract class ByteSequence implements Comparable<ByteSequence> {
    private final byte[] _bytes;
    private final int offset;
    private final int size;

    /**
     * @param _bytes Underlying array of {@code byte}.
     * @param offset The start position of the sequence within the byte array.
     * @param size The number of bytes this sequence represents.
     */
    ByteSequence(@NotNull byte[] _bytes, int offset, int size) {
        requireNotNull(_bytes, "_bytes must not be null");
        this._bytes = _bytes;
        this.offset = offset;
        this.size = size;
    }

    /**
     * @return offset
     */
    public final int getOffset() {
        return offset;
    }

    /**
     * @return size
     */
    public final int getSize() {
        return size;
    }

    /**
     * The underlying bytes.  Some implementations may choose to make a copy of the underlying {@code byte[]} for
     * security reasons.  For example, {@link OpaqueBytes}.
     */
    @NotNull
    public abstract byte[] getBytes();

    /** Returns a {@link ByteArrayInputStream} of the bytes. */
    @NotNull
    public final ByteArrayInputStream open() {
        return new ByteArrayInputStream(_bytes, offset, size);
    }

    /**
     * Create a sub-sequence of this sequence. A copy of the underlying array may be made, if a subclass overrides
     * {@code bytes} to do so, as {@link OpaqueBytes} does.
     *
     * @param offset The offset within this sequence to start the new sequence. Note: not the offset within the backing array.
     * @param size The size of the intended sub-sequence.
     */
    @SuppressWarnings("MemberVisibilityCanBePrivate")
    @NotNull
    public ByteSequence subSequence(int offset, int size) {
        if (offset == 0 && size == this.size) {
            return this;
        } else {
            // Intentionally use getBytes() rather than _bytes, to mirror the copy-or-not behaviour of that property.
            return new OpaqueBytesSubSequence(getBytes(), this.offset + offset, size);
        }
    }

    /**
     * Take the first n bytes of this sequence as a sub-sequence.  See {@link #subSequence} for further semantics.
     */
    @NotNull
    public ByteSequence take(int n) {
        return subSequence(0, n);
    }

    /**
     * A new read-only {@link ByteBuffer} view of this sequence or part of it.
     * If {@code start} or {@code end} are negative then {@link IllegalArgumentException} is thrown, otherwise they are clamped if necessary.
     * This method cannot be used to get bytes before {@code offset} or after {@code offset}+{@code size}, and never makes a new array.
     * @param start start index of slice (inclusive)
     * @param end end index of slice (exclusive)
     * @return {@link ByteBuffer}
     */
    @NotNull
    public final ByteBuffer slice(int start, int end) {
        if (start < 0) {
            throw new IllegalArgumentException("Starting index must be greater than or equal to 0");
        }
        if (end < 0) {
            throw new IllegalArgumentException("End index must be greater or equal to 0");
        }
        int clampedStart = min(start, size);
        int clampedEnd=min(end, size);
        return ByteBuffer.wrap(_bytes, offset+clampedStart, max(0, clampedEnd - clampedStart)).asReadOnlyBuffer();
    }

    /**
     * @param start index of slice
     * @return {@link ByteBuffer}
     */
    @NotNull
    public final ByteBuffer slice(int start) {
        return slice(start, size);
    }

    /**
     * @return {@link ByteBuffer}
     */
    @NotNull
    public final ByteBuffer slice() {
        return slice(0, size);
    }

    /** Write this sequence to an {@link OutputStream}. */
    public final void writeTo(@NotNull OutputStream output) throws IOException {
        requireNotNull(output, "output may not be null");
        output.write(_bytes, offset, size);
    }

    /** Write this sequence to a {@link ByteBuffer}. */
    @NotNull
    public final ByteBuffer putTo(@NotNull ByteBuffer buffer) {
        requireNotNull(buffer, "buffer may not be null");
        return buffer.put(_bytes, offset, size);
    }

    /**
     * Copy this sequence, complete with new backing array.  This can be helpful to break references to potentially
     * large backing arrays from small sub-sequences.
     * @return deep-copy of byte-sequence
     */
    @NotNull
    public final ByteSequence copy() {
        return new OpaqueBytesSubSequence(copyBytes(), 0, size);
    }

    /**
     * Same as {@link #copy} but returns just the new byte array.
     * @return byte array
     */
    @NotNull
    public final byte[] copyBytes() {
        return copyOfRange(_bytes, offset, offset + size);
    }

    /**
     * Compare byte arrays byte by byte.  Arrays that are shorter are deemed less than longer arrays if all the bytes
     * of the shorter array equal those in the same position of the longer array.
     */
    @Override
    public int compareTo(@NotNull ByteSequence other) {
        int min = min(this.size, other.size);
        // Compare min bytes
        for(int index = 0; index < min; ++index) {
            int unsignedThis = Byte.toUnsignedInt(_bytes[this.offset + index]);
            int unsignedOther = Byte.toUnsignedInt(other._bytes[other.offset + index]);
            if (unsignedThis != unsignedOther) {
                return Integer.signum(unsignedThis - unsignedOther);
            }
        }
        // First min bytes is the same, so now resort to size.
        return Integer.signum(this.size - other.size);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ByteSequence)) {
            return false;
        }
        final ByteSequence other = (ByteSequence) obj;
        if (size != other.size) {
            return false;
        } else {
            return subArraysEqual(this._bytes, this.offset, this.size, other._bytes, other.offset);
        }
    }

    private boolean subArraysEqual(@NotNull byte[] a, int aOffset, int length, @NotNull byte[] b, int bOffset) {
        int bytesRemaining = length;
        int aPos = aOffset;
        int bPos = bOffset;
        while (bytesRemaining-- > 0) {
            if (a[aPos++] != b[bPos++]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        var result = 1;
        for (int index = 0; index < offset + size; ++index) {
            result = 31 * result + _bytes[index];
        }
        return result;
    }

    @Override
    @NotNull
    public String toString() {
        return '[' + toHexString(copyBytes()) + ']';
    }
}
