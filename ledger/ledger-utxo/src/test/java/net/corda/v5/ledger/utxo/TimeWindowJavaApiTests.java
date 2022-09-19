package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

public final class TimeWindowJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getFromShouldReturnTheExpectedValue() {
        Instant value = timeWindow.getFrom();
        Assertions.assertEquals(minInstant, value);
    }

    @Test
    public void getUntilShouldReturnTheExpectedValue() {
        Instant value = timeWindow.getUntil();
        Assertions.assertEquals(maxInstant, value);
    }

    @Test
    public void getMidpointShouldReturnTheExpectedValue() {
        Instant value = timeWindow.getMidpoint();
        Assertions.assertEquals(midpoint, value);
    }

    @Test
    public void getDurationShouldReturnTheExpectedValue() {
        Duration value = timeWindow.getDuration();
        Assertions.assertEquals(duration, value);
    }

    @Test
    public void containsShouldReturnTheExpectedValue() {
        boolean value = timeWindow.contains(midpoint);
        Assertions.assertTrue(value);
    }
}
