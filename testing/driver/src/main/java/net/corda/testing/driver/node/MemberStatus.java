package net.corda.testing.driver.node;

import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

public enum MemberStatus {
    ACTIVE,
    PENDING,
    SUSPENDED;

    @NotNull
    public static MemberStatus fromString(@NotNull String value) {
        requireNonNull(value, "value must not be null");
        for (MemberStatus status : values()) {
            if (status.toString().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown MemberStatus '" + value + '\'');
    }
}
