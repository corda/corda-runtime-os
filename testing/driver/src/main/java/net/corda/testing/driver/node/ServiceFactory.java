package net.corda.testing.driver.node;

import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

@DoNotImplement
public interface ServiceFactory {
    @NotNull
    <T> T getService(@NotNull Class<T> serviceType, @Nullable String filter, @NotNull Duration timeout);

    void shutdown();
}
