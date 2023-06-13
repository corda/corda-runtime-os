package net.corda.testing.driver.function;

import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
    R applyThrowing(T item) throws Exception;

    default R apply(T item) {
        try {
            return applyThrowing(item);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
