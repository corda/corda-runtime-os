package net.corda.v5.ledger.obsolete.contracts;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class CommandsJavaApiTest {
    private final CommandData commandData = mock(CommandData.class);
    private final PublicKey publicKey = mock(PublicKey.class);


    @Nested
    public class CommandJavaApiTest {
        private final Command<CommandData> command = new Command<>(commandData, publicKey);

        @Test
        public void initialize() {
            final Command<CommandData> command1 = new Command<>(commandData, List.of(publicKey));

            Assertions.assertThat(command).isNotNull();
            Assertions.assertThat(command1).isNotNull();
        }

        @Test
        public void value() {
            final CommandData value = command.getValue();

            Assertions.assertThat(value).isNotNull();
        }

        @Test
        public void signers() {
            final List<PublicKey> publicKeys = command.getSigners();

            Assertions.assertThat(publicKeys).isNotNull();
        }
    }

    @Nested
    public class TypeOnlyCommandDataJavaApiTest {

        @Test
        public void initialize() {
            final CustomTypeOnlyCommandData customTypeOnlyCommandData = new CustomTypeOnlyCommandData();
            final CustomTypeOnlyCommandData customTypeOnlyCommandData1 = new CustomTypeOnlyCommandData();

            Assertions.assertThat(customTypeOnlyCommandData).isNotNull();
            Assertions.assertThat(customTypeOnlyCommandData1).isNotNull();
            Assertions.assertThat(customTypeOnlyCommandData).isEqualTo(customTypeOnlyCommandData1);
        }

        class CustomTypeOnlyCommandData extends TypeOnlyCommandData {

        }
    }

    @Nested
    public class MoveCommandJavaApiTest {

        @Test
        public void contract() {
            final MoveCommand moveCommand = mock(MoveCommand.class);
            Class<Contract> contractClass = Contract.class;
            doReturn(contractClass).when(moveCommand).getContract();

            final Class<?> contractClass1 = moveCommand.getContract();

            Assertions.assertThat(contractClass1).isNotNull();
        }
    }
}
