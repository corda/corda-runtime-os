package net.corda.v5.serialization;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.types.OpaqueBytes;
import org.jetbrains.annotations.NotNull;

/**
 * A type safe wrapper around a byte array that contains a serialised object.
 */
@SuppressWarnings("unused")
@CordaSerializable
public final class SerializedBytes<T> extends OpaqueBytes {
    public SerializedBytes(@NotNull byte[] bytes) {
        super(bytes);
    }

    @NotNull
    public String getSummary() {
        return "SerializedBytes(size = " + getSize() + ')';
    }
}
