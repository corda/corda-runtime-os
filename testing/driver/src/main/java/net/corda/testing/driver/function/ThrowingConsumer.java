package net.corda.testing.driver.function;

import java.util.function.Consumer;

@FunctionalInterface
public interface ThrowingConsumer<T> extends Consumer<T> {
    void acceptThrowing(T item) throws Exception;

    default void accept(T item) {
        try {
            acceptThrowing(item);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
