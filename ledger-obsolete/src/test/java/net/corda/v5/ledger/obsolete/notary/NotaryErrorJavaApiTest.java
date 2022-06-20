package net.corda.v5.ledger.obsolete.notary;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.contracts.StateRef;
import net.corda.v5.ledger.obsolete.contracts.TimeWindow;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class NotaryErrorJavaApiTest {
    private final SecureHash secureHash = SecureHash.create("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
    private final StateConsumptionDetails stateConsumptionDetails = new StateConsumptionDetails(
            secureHash, StateConsumptionDetails.ConsumedStateType.INPUT_STATE
    );
    private final Throwable throwable = new Throwable();

    @Nested
    class ConflictJavaApiTest {
        private final StateRef stateRef = new StateRef(secureHash, 1);
        private final Map<StateRef, StateConsumptionDetails> map = Map.of(stateRef, stateConsumptionDetails);
        private final NotaryError.Conflict conflict = new NotaryError.Conflict(secureHash, map);

        @Test
        public void getTxId() {
            SecureHash result = conflict.getTxId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHash);
        }

        @Test
        public void getConsumedStates() {
            Map<StateRef, StateConsumptionDetails> result = conflict.getConsumedStates();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(map);
        }
    }

    @Nested
    class TimeWindowInvalidJavaApiTest {
        private final TimeWindow timeWindow = mock(TimeWindow.class);
        private final Instant instant = Instant.MAX;
        private final NotaryError.TimeWindowInvalid timeWindowInvalid = new NotaryError.TimeWindowInvalid(instant, timeWindow);

        @Test
        public void getCurrentTime() {
            Instant result = timeWindowInvalid.getCurrentTime();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(instant);
        }

        @Test
        public void getTxTimeWindow() {
            TimeWindow result = timeWindowInvalid.getTxTimeWindow();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(timeWindow);
        }
    }

    @Nested
    class TransactionInvalidJavaApiTest {
        private final NotaryError.TransactionInvalid transactionInvalid = new NotaryError.TransactionInvalid(throwable);

        @Test
        public void getCause() {
            Throwable result = transactionInvalid.getCause();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(throwable);
        }
    }

    @Nested
    class RequestSignatureInvalidJavaApiTest {
        private final NotaryError.RequestSignatureInvalid requestSignatureInvalid = new NotaryError.RequestSignatureInvalid(throwable);

        @Test
        public void getCause() {
            Throwable result = requestSignatureInvalid.getCause();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(throwable);
        }
    }

    @Nested
    class GeneralJavaApiTest {
        private final NotaryError.General requestSignatureInvalid = new NotaryError.General(throwable);

        @Test
        public void getCause() {
            Throwable result = requestSignatureInvalid.getCause();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(throwable);
        }
    }


    @Nested
    class NotaryExceptionJavaApiTest {
        private final NotaryError notaryError = mock(NotaryError.class);
        private final NotaryException notaryException = new NotaryException(notaryError, secureHash);
        private final NotaryException notaryExceptionWithoutId = new NotaryException(notaryError);

        @Test
        public void getError() {
            NotaryError result = notaryException.getError();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(notaryError);
        }

        @Test
        public void getTxId() {
            SecureHash result = notaryException.getTxId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHash);
        }

        @Test
        public void getExceptionWithoutId() {
            SecureHash result = notaryExceptionWithoutId.getTxId();

            Assertions.assertThat(result).isNull();
        }
    }

    @Nested
    class StateConsumptionDetailsJavaApiTest {

        @Test
        public void getHashOfTransactionId() {
            SecureHash result = stateConsumptionDetails.getHashOfTransactionId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(secureHash);
        }

        @Test
        public void getType() {
            StateConsumptionDetails.ConsumedStateType result = stateConsumptionDetails.getType();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(StateConsumptionDetails.ConsumedStateType.INPUT_STATE);
        }

        @Test
        public void copy() {
            final SecureHash newSecureHash = SecureHash.create("SHA-256:7A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581B");
            StateConsumptionDetails result = stateConsumptionDetails.copy(newSecureHash);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result.getHashOfTransactionId()).isEqualTo(newSecureHash);
        }

    }
}
