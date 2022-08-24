package net.corda.v5.ledger.obsolete.contracts;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.obsolete.UniqueIdentifier;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatePointersJavaApiTest {
    private final Class<ContractState> aClass = ContractState.class;

    @Nested
    public class StatePointerJavaApiTest {
        private final StatePointer<ContractState> statePointer = mock(StatePointer.class);


        @Test
        public void pointer() {
            final Object object = new Object();
            when(statePointer.getPointer()).thenReturn(object);

            final Object obj = statePointer.getPointer();

            Assertions.assertThat(obj).isEqualTo(object);
        }

        @Test
        public void type() {
            when(statePointer.getType()).thenReturn(aClass);

            final Class<ContractState> aClass1 = statePointer.getType();

            Assertions.assertThat(aClass1).isEqualTo(aClass);
        }

        @Test
        public void isResolved() {
            when(statePointer.isResolved()).thenReturn(true);

            final Boolean aBoolean = statePointer.isResolved();

            Assertions.assertThat(aBoolean).isTrue();
        }
    }

    @Nested
    public class StaticPointerJavaApiTest {
        private final SecureHash secureHash =
                SecureHash.parse("SHA-256:6A1687C143DF792A011A1E80670A4E4E0C25D0D87A39514409B1ABFC2043581A");
        private final StateRef stateRef = new StateRef(secureHash, 6);
        private final StaticPointer<ContractState> staticPointer = new StaticPointer<>(stateRef, aClass);

        @Test
        public void test_initialization() {
            final StateRef stateRef1 = new StateRef(secureHash, 5);
            final StaticPointer<ContractState> staticPointer1 = new StaticPointer<>(stateRef1, aClass, true);

            Assertions.assertThat(staticPointer).isNotNull();
            Assertions.assertThat(staticPointer1).isNotNull();
            Assertions.assertThat(staticPointer).isNotEqualTo(staticPointer1);
        }

        @Test
        public void pointer() {
            final StateRef stateRef1 = staticPointer.getPointer();

            Assertions.assertThat(stateRef1).isEqualTo(stateRef);
        }

        @Test
        public void type() {
            final Class<ContractState> aClass1 = staticPointer.getType();

            Assertions.assertThat(aClass1).isEqualTo(aClass);
        }

        @Test
        public void isResolved() {
            final Boolean isResolved = staticPointer.isResolved();

            Assertions.assertThat(isResolved).isFalse();
        }
    }

    @Nested
    public class LinearPointerJavaApiTest {
        private final UniqueIdentifier identifier = new UniqueIdentifier();
        private final Class<LinearState> stateClass = LinearState.class;
        private final LinearPointer<LinearState> linearPointer = new LinearPointer<>(identifier, stateClass);

        @Test
        public void test_initialization() {
            final LinearPointer<LinearState> linearPointer1 = new LinearPointer<>(new UniqueIdentifier(), stateClass, false);

            Assertions.assertThat(linearPointer1).isNotNull();
            Assertions.assertThat(linearPointer).isNotNull();
            Assertions.assertThat(linearPointer1).isNotEqualTo(linearPointer);
        }

        @Test
        public void pointer() {
            final UniqueIdentifier identifier1 = linearPointer.getPointer();

            Assertions.assertThat(identifier1).isEqualTo(identifier);
        }

        @Test
        public void type() {
            final Class<LinearState> stateClass1 = linearPointer.getType();

            Assertions.assertThat(stateClass1).isEqualTo(stateClass);
        }

        @Test
        public void isResolved() {
            final Boolean isResolved = linearPointer.isResolved();

            Assertions.assertThat(isResolved).isTrue();
        }
    }
}
