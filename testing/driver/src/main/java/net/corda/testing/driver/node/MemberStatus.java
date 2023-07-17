package net.corda.testing.driver.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public enum MemberStatus {
    ACTIVE,
    PENDING,
    SUSPENDED;

    @NotNull
    public static MemberStatus fromString(@Nullable String value) {
        for (MemberStatus status : values()) {
            if (status.toString().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown MemberStatus '" + value + '\'');
    }
}
