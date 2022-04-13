package net.corda.v5.ledger.services.vault.events;

import net.corda.v5.base.stream.Cursor.PollResult;
import net.corda.v5.base.stream.Cursor.PollResult.PositionedValue;
import net.corda.v5.base.stream.DurableCursor;
import net.corda.v5.crypto.DigestAlgorithmName;
import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.contracts.ContractState;
import net.corda.v5.ledger.contracts.StateAndRef;
import net.corda.v5.ledger.contracts.StateRef;
import net.corda.v5.ledger.contracts.TransactionState;
import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.ledger.identity.Party;
import net.corda.v5.ledger.services.vault.VaultEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the {@link DurableCursor} API tied to {@link VaultStateEventService} is usable from Java.
 */
public class VaultStateEventCursorJavaTest {

    private static final Logger log = LoggerFactory.getLogger(VaultStateEventCursorJavaTest.class);

    private final DurableCursor<VaultStateEvent<ContractState>> cursor = mock(DurableCursor.class);

    private static class DummyState implements ContractState {
        @NotNull
        @Override
        public List<AbstractParty> getParticipants() {
            return List.of();
        }
    }

    private final PollResult<VaultStateEvent<ContractState>> result = createPollResult();

    private final CountDownLatch latch = new CountDownLatch(5);

    private static SecureHash createRandomHash() {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        return new SecureHash(DigestAlgorithmName.SHA2_256.getName(), randomBytes);
    }

    @Test
    public void exampleJavaUsage() throws InterruptedException {
        when(cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES))).thenReturn(result);
        Thread thread = new Thread(this::run);
        thread.start();
        latch.await(5, TimeUnit.SECONDS);
        thread.interrupt();
    }

    private void run() {
        while (!Thread.currentThread().isInterrupted()) {
            PollResult<VaultStateEvent<ContractState>> result = cursor.poll(50, Duration.of(5, ChronoUnit.MINUTES));
            if (!result.isEmpty()) {
                for (PositionedValue<VaultStateEvent<ContractState>> positionedValue : result.getPositionedValues()) {
                    log.info("Processing value: " + positionedValue.getValue() + " at position: " + positionedValue.getPosition());
                    StateAndRef<? extends ContractState> stateAndRef = positionedValue.getValue().getStateAndRef();
                    ContractState state = stateAndRef.getState().getData();
                    if (state instanceof DummyState) {
                        doStuffWithState((DummyState) state);
                        doStuffWithStateAndRef((StateAndRef<DummyState>) stateAndRef);
                    }
                    latch.countDown();
                }
                cursor.commit(result.getLastPosition());
            }
        }
    }

    private void doStuffWithState(DummyState state) {
        log.info("Doing stuff with state: " + state);
    }

    private void doStuffWithStateAndRef(StateAndRef<DummyState> stateAndRef) {
        log.info("Doing stuff with stateAndRef: " + stateAndRef);
    }

    private PollResult<VaultStateEvent<ContractState>> createPollResult() {
        return new PollResult<>() {
            @NotNull
            @Override
            public List<PositionedValue<VaultStateEvent<ContractState>>> getPositionedValues() {
                return List.of(
                        new PositionedValue<>() {
                            @Override
                            public VaultStateEvent<ContractState> getValue() {
                                return new VaultStateEvent<>() {
                                    @NotNull
                                    @Override
                                    public StateAndRef<ContractState> getStateAndRef() {
                                        return new StateAndRef<>(
                                                new TransactionState<>(
                                                        new DummyState(),
                                                        "",
                                                        mock(Party.class)
                                                ),
                                                new StateRef(createRandomHash(), 0)
                                        );
                                    }

                                    @NotNull
                                    @Override
                                    public VaultEventType getEventType() {
                                        return VaultEventType.PRODUCE;
                                    }

                                    @NotNull
                                    @Override
                                    public Instant getTimestamp() {
                                        return Instant.now();
                                    }
                                };
                            }

                            @Override
                            public long getPosition() {
                                return 1;
                            }
                        }
                );
            }

            @Nullable
            @Override
            public Long getRemainingElementsCountEstimate() {
                return 1000L;
            }

            @Override
            public boolean isLastResult() {
                return false;
            }
        };
    }
}
