package net.corda.v5.ledger.utxo.observer;

import net.corda.v5.crypto.SecureHash;
import net.corda.v5.ledger.utxo.ContractState;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.security.PublicKey;
import java.util.List;

/**
 * Example contact State
 */
public class ExampleStateJ implements ContractState {
    public List<PublicKey> participants;
    public SecureHash issuer;
    public String currency;
    public BigDecimal amount;

    @NotNull
    @Override
    public List<PublicKey> getParticipants() {
        return participants;
    }
}
