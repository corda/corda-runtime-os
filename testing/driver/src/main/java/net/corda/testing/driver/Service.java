package net.corda.testing.driver;

import java.util.function.Supplier;
import net.corda.testing.driver.function.ThrowingConsumer;
import net.corda.testing.driver.function.ThrowingFunction;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

@DoNotImplement
public interface Service<T> extends Supplier<T>, AutoCloseable {
    @NotNull
    default Service<T> andAlso(@NotNull ThrowingConsumer<T> action) throws Exception {
        requireNonNull(action, "action must not be null");
        action.acceptThrowing(get());
        return this;
    }

    default <R> R andThen(@NotNull ThrowingFunction<T, R> action) throws Exception {
        requireNonNull(action, "action must not be null");
        return action.applyThrowing(get());
    }
}
