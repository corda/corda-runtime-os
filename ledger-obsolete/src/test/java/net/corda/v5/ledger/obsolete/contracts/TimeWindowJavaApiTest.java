package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.NANOS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimeWindowJavaApiTest {
    private final TimeWindow timeWindow = mock(TimeWindow.class);
    private final Instant now = Instant.now();
    private final Instant after = now.plus(200L, NANOS);

    @Test
    public void fromOnly() {
        final TimeWindow timeWindow1 = TimeWindow.fromOnly(now);

        Assertions.assertThat(timeWindow1).isNotNull();
    }

    @Test
    public void untilOnly() {
        final TimeWindow timeWindow1 = TimeWindow.untilOnly(after);

        Assertions.assertThat(timeWindow1).isNotNull();
    }

    @Test
    public void between() {
        final TimeWindow timeWindow1 = TimeWindow.between(now, after);

        Assertions.assertThat(timeWindow1).isNotNull();
    }

    @Test
    public void fromStartAndDuration() {
        final TimeWindow timeWindow1 = TimeWindow.fromStartAndDuration(now, Duration.ofDays(1));

        Assertions.assertThat(timeWindow1).isNotNull();
    }

    @Test
    public void withTolerance() {
        final TimeWindow timeWindow1 = TimeWindow.withTolerance(now, Duration.ofDays(2));

        Assertions.assertThat(timeWindow1).isNotNull();
    }

    @Test
    public void fromTime() {
        when(timeWindow.getFromTime()).thenReturn(now);

        final Instant fromTime = timeWindow.getFromTime();

        Assertions.assertThat(fromTime).isEqualTo(now);
    }

    @Test
    public void untilTime() {
        when(timeWindow.getUntilTime()).thenReturn(after);

        final Instant untilTime = timeWindow.getUntilTime();

        Assertions.assertThat(untilTime).isEqualTo(after);
    }

    @Test
    public void midpoint() {
        when(timeWindow.getMidpoint()).thenReturn(now);

        final Instant midpoint = timeWindow.getMidpoint();

        Assertions.assertThat(midpoint).isEqualTo(now);
    }

    @Test
    public void length() {
        final Duration duration = Duration.ofDays(1);
        when(timeWindow.getLength()).thenReturn(duration);

        final Duration duration1 = timeWindow.getLength();

        Assertions.assertThat(duration1).isEqualTo(duration);
    }

    @Test
    public void contains() {
        when(timeWindow.contains(now)).thenReturn(true);

        final Boolean isContained = timeWindow.contains(now);

        Assertions.assertThat(isContained).isTrue();
    }
}
