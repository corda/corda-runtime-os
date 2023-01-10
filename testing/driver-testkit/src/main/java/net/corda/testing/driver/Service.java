package net.corda.testing.driver;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.corda.v5.base.annotations.DoNotImplement;
import org.jetbrains.annotations.NotNull;

@DoNotImplement
public interface Service<T> extends Supplier<T>, AutoCloseable {
    @NotNull
    default Service<T> andAlso(@NotNull Consumer<T> action) {
        action.accept(get());
        return this;
    }

    default <R> R andThen(@NotNull Function<T, R> action) {
        return action.apply(get());
    }
}
