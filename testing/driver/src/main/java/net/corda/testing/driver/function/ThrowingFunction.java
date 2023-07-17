package net.corda.testing.driver.function;

import org.jetbrains.annotations.NotNull;
import java.util.function.Function;

@FunctionalInterface
public interface ThrowingFunction<T, R> extends Function<T, R> {
    R applyThrowing(@NotNull T item) throws Exception;

    default R apply(@NotNull T item) {
        try {
            return applyThrowing(item);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
