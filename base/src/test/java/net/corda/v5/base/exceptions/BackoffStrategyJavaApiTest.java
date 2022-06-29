package net.corda.v5.base.exceptions;

import net.corda.v5.base.exceptions.BackoffStrategy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class BackoffStrategyJavaApiTest {
    @Test
    public void canBeUsedAsLambda() {
        var attempt = 1;
        var backoff = callee(attempt, (i -> {
            if (i == 1) {
                return 1;
            } else {
                return -1;
            }
        }));
        assertEquals(1, backoff);
    }

    @Test
    public void canCreateBackoff() {
        assertNotNull(BackoffStrategy.createExponentialBackoff());
        assertNotNull(BackoffStrategy.createExponentialBackoff(2, 100));
        assertNotNull(BackoffStrategy.createLinearBackoff());
        assertNotNull(BackoffStrategy.createBackoff(2, List.of(100L)));
    }

    @Test
    public void canCreateDefaultImplementationDirectly() {
        new BackoffStrategy.Default(new Long[]{100L, 200L});
    }

    private long callee(int attempt, BackoffStrategy strategy) {
        return strategy.getBackoff(attempt);
    }
}
