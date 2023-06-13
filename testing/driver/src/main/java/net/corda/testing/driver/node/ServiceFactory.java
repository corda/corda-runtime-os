package net.corda.testing.driver.node;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

public interface ServiceFactory {
    @NotNull
    <T> T getService(@NotNull Class<T> serviceType, @Nullable String filter, @NotNull Duration timeout);

    void shutdown();
}
