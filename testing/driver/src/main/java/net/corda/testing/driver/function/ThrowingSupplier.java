package net.corda.testing.driver.function;

import java.util.function.Supplier;

@FunctionalInterface
public interface ThrowingSupplier<T> extends Supplier<T> {
    T getThrowing() throws Exception;

    default T get() {
        try {
            return getThrowing();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
