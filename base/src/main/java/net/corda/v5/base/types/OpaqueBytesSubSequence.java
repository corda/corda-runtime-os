package net.corda.v5.base.types;

import net.corda.v5.base.annotations.CordaSerializable;
import org.jetbrains.annotations.NotNull;

/**
 * Class is public for serialization purposes.
 */
@CordaSerializable
public final class OpaqueBytesSubSequence extends ByteSequence {
    private final byte[] bytes;

    public OpaqueBytesSubSequence(@NotNull byte[] bytes, int offset, int size) {
        super(bytes, offset, size);
        if (offset < 0 || offset >= bytes.length) {
            throw new IllegalArgumentException("Offset must be greater than or equal to 0, and less than the size of the backing array");
        }
        if (size < 0 || offset + size > bytes.length) {
            throw new IllegalArgumentException("Sub-sequence size must be greater than or equal to 0, and less than the size of the backing array");
        }
        this.bytes = bytes;
    }

    @NotNull
    public byte[] getBytes() {
        return bytes;
    }
}
