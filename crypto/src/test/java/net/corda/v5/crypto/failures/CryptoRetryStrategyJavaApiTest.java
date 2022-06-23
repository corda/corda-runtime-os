package net.corda.v5.crypto.failures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CryptoRetryStrategyJavaApiTest {
    @Test
    public void canBeUsedAsLambda() {
        var backoff = callee(1, 1000, ((attempt, currentBackoffMillis) -> {
            if(attempt == 1) {
                return currentBackoffMillis;
            } else {
                return -1;
            }
        }));
        assertEquals(1000, backoff);
    }

    private long callee(int attempt, long current, CryptoRetryStrategy strategy) {
        return strategy.getBackoff(attempt, current);
    }
}
