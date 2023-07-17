package net.corda.testing.driver.function;

import org.jetbrains.annotations.NotNull;
import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
    void acceptThrowing(@NotNull T item) throws Exception;

    default void accept(@NotNull T item) {
        try {
            acceptThrowing(item);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
