package net.corda.v5.ledger.services.vault.events;

import net.corda.v5.base.stream.DurableCursor;
import net.corda.v5.ledger.contracts.ContractState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link VaultStateEventService} API is usable from Java.
 */
public class VaultStateEventServiceJavaTest {

    private static final Logger log = LoggerFactory.getLogger(VaultStateEventServiceJavaTest.class);

    private final DurableCursor<VaultStateEvent<ContractState>> cursor = mock(DurableCursor.class);

    private final VaultStateEventService vaultStateEventService = mock(VaultStateEventService.class);

    @BeforeEach
    public void setup() {
        when(vaultStateEventService.subscribe(any())).thenReturn(cursor);
    }

    @Test
    public void subscribeCursor() {
        DurableCursor<VaultStateEvent<ContractState>> cursor = vaultStateEventService.subscribe("give me a cursor");
        cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
        cursor.commit(1);
    }

    @Test
    public void subscribeCallback() {
        vaultStateEventService.subscribe(
            "give me a subscriber",
            (deduplicationId, event) -> log.info("Processing event: " + event + " with deduplication id: " + deduplicationId)
        );

        vaultStateEventService.subscribe("give me a cursor", (deduplicationId, event) -> {
            log.info("Processing event: " + event + " with deduplication id: " + deduplicationId);
        });
    }
}
