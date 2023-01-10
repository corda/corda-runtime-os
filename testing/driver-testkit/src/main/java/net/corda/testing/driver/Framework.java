package net.corda.testing.driver;

import java.util.concurrent.TimeoutException;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@DoNotImplement
public interface Framework {
    @NotNull
    <T> Service<T> getService(
        @NotNull Class<T> serviceType,
        @Nullable String filter,
        long timeout
    ) throws InterruptedException, TimeoutException;

    @NotNull
    <T> Service<T> getService(
        @NotNull Class<T> serviceType,
        long timeout
    ) throws InterruptedException, TimeoutException;
}
