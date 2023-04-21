package net.corda.v5.serialization;

import net.corda.v5.base.annotations.CordaSerializable;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

/**
 * A type safe wrapper around a byte array that contains a serialised object.
 */
@SuppressWarnings("unused")
@DoNotImplement
@CordaSerializable
public interface SerializedBytes<T> {

    @NotNull
    byte[] getBytes();
}
