package net.corda.v5.testing;

import org.jetbrains.annotations.NotNull;

public interface MessageProvider {
    @NotNull
    String getMessage();
}
