package net.corda.v5.serialization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.NotSerializableException;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

/**
 * Thrown by the serialization framework, probably indicating that a custom serializer
 * needs to be included in a transaction.
 */
public class MissingSerializerException extends NotSerializableException {
    private final String typeDescriptor;
    private final List<String> typeNames;

    private MissingSerializerException(
        @NotNull String message,
        @Nullable String typeDescriptor,
        @NotNull List<String> typeNames
    ) {
        super(message);
        requireNonNull(message, "message may not be null");
        requireNonNull(typeNames, "typeNames may not be null");
        this.typeDescriptor = typeDescriptor;
        this.typeNames = List.copyOf(typeNames);
    }

    public MissingSerializerException(@NotNull String message, @NotNull String typeDescriptor) {
        this(message, typeDescriptor, emptyList());
        requireNonNull(typeDescriptor, "typeDescriptor may not be null");
    }

    public MissingSerializerException(@NotNull String message, @NotNull List<String> typeNames) {
        this(message, null, typeNames);
    }

    @Nullable
    public String getTypeDescriptor() {
        return typeDescriptor;
    }

    @NotNull
    public List<String> getTypeNames() {
        return typeNames;
    }
}
