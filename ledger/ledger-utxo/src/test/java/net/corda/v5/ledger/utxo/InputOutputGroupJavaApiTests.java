package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

public final class InputOutputGroupJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void getInputsShouldReturnTheExpectedValue() {
        List<StateAndRef<IdentifiableState>> value = inputOutputGroup.getInputs();
        Assertions.assertEquals(List.of(identifiableStateAndRef), value);
    }

    @Test
    public void getOutputsShouldReturnTheExpectedValue() {
        List<StateAndRef<IdentifiableState>> value = inputOutputGroup.getOutputs();
        Assertions.assertEquals(List.of(identifiableStateAndRef), value);
    }

    @Test
    public void getGroupingKeyShouldReturnTheExpectedValue() {
        UUID value = inputOutputGroup.getGroupingKey();
        Assertions.assertEquals(id, value);
    }
}
