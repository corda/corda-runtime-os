package net.corda.v5.ledger.utxo;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public final class CpkConstraintJavaApiTests extends AbstractMockTestHarness {

    @Test
    public void isSatisfiedByShouldBeCallable() {
        boolean value = constraint.isSatisfiedBy(digestService, constraintContext);
        Assertions.assertTrue(value);
    }
}
