package net.corda.v5.ledger.schemas;

import net.corda.v5.ledger.identity.AbstractParty;
import net.corda.v5.persistence.MappedSchema;
import org.assertj.core.api.Assertions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PersistentTypesJavaApiTest {

    private final PersistentStateRef persistentStateRef = new PersistentStateRef("transaction_id", 1);
    private final PersistentState persistentState = new PersistentState(persistentStateRef);
    private final DirectStatePersistableTest directStatePersistableTest = new DirectStatePersistableTest();

    @Nested
    class QueryableStateJavaApiTest {
        private final QueryableState queryableState = mock(QueryableState.class);
        private final AbstractParty party = mock(AbstractParty.class);
        private final MappedSchema mappedSchema = new MappedSchema(IndirectStatePersistableTest.class, 1, List.of(IndirectStatePersistableTest.class));
        private final List<MappedSchema> mappedSchemas = List.of(mappedSchema);
        private final QueryableStateTest queryableStateTest = new QueryableStateTest();

        @Test
        public void supportedSchemas() {
            when(queryableState.supportedSchemas()).thenReturn(mappedSchemas);

            Iterable<MappedSchema> result = queryableState.supportedSchemas();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(mappedSchemas);
        }

        @Test
        public void generateMappedObject() {
            when(queryableState.generateMappedObject(mappedSchema)).thenReturn(persistentState);

            PersistentState result = queryableState.generateMappedObject(mappedSchema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(persistentState);
        }

        @Test
        public void supportedSchemasImpl() {
            Iterable<MappedSchema> result = queryableStateTest.supportedSchemas();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(mappedSchemas);
        }

        @Test
        public void generateMappedObjectImpl() {
            PersistentState result = queryableStateTest.generateMappedObject(mappedSchema);

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(persistentState);
        }

        @Test
        public void getParticipants() {
            List<AbstractParty> result = queryableStateTest.getParticipants();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(List.of(party));
        }

        class QueryableStateTest implements QueryableState {

            @NotNull
            @Override
            public List<AbstractParty> getParticipants() {
                return List.of(party);
            }

            @NotNull
            @Override
            public Iterable<MappedSchema> supportedSchemas() {
                return mappedSchemas;
            }

            @NotNull
            @Override
            public PersistentState generateMappedObject(@NotNull MappedSchema schema) {
                return persistentState;
            }
        }
    }

    @Nested
    class PersistentStateJavaApiTest {
        @Test
        public void getStateRef() {
            PersistentStateRef result = persistentState.getStateRef();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(persistentStateRef);
        }

        @Test
        public void getStateRefImpl() {
            final PersistentStateTest persistentStateTest = new PersistentStateTest(persistentStateRef);

            PersistentStateRef result = persistentStateTest.getStateRef();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(persistentStateRef);
        }

        class PersistentStateTest extends PersistentState {

            PersistentStateTest(PersistentStateRef stateRef) {
                super(stateRef);
            }
        }
    }

    @Nested
    class PersistentStateRefJavaApiTest {
        @Test
        public void getTxId() {
            String result = persistentStateRef.getTxId();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo("transaction_id");
        }

        @Test
        public void getIndex() {
            int result = persistentStateRef.getIndex();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(1);
        }
    }

    @Nested
    class DirectStatePersistableJavaApiTest {
        private final DirectStatePersistable directStatePersistable = new DirectStatePersistableTest();

        @Test
        public void getTxId() {
            PersistentStateRef result = directStatePersistable.getStateRef();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(persistentStateRef);
        }
    }

    @Nested
    class IndirectStatePersistableJavaApiTest {
        private final IndirectStatePersistableTest indirectStatePersistableTest = new IndirectStatePersistableTest();

        @Test
        public void getTxId() {
            DirectStatePersistableTest result = indirectStatePersistableTest.getCompositeKey();

            Assertions.assertThat(result).isNotNull();
            Assertions.assertThat(result).isEqualTo(directStatePersistableTest);
        }
    }

    class IndirectStatePersistableTest implements IndirectStatePersistable<DirectStatePersistableTest> {

        @NotNull
        @Override
        public DirectStatePersistableTest getCompositeKey() {
            return directStatePersistableTest;
        }
    }

    class DirectStatePersistableTest implements DirectStatePersistable {

        @Nullable
        @Override
        public PersistentStateRef getStateRef() {
            return persistentStateRef;
        }
    }
}
