package com.r3.corda.testing.smoketests.ion.states;

import com.r3.corda.testing.smoketests.ion.contracts.SecurityTokenContract;
import net.corda.v5.base.annotations.ConstructorForDeserialization;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.ledger.utxo.BelongsToContract;
import net.corda.v5.ledger.utxo.ContractState;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

@BelongsToContract(SecurityTokenContract.class)
public class SecurityToken implements ContractState {

    private String cusip;
    private Long quantity;
    private MemberX500Name holder;
    private MemberX500Name tokenForger;
    public List<PublicKey> participants;

    @ConstructorForDeserialization
    public SecurityToken(String cusip, Long quantity, MemberX500Name holder, MemberX500Name tokenForger, List<PublicKey> participants) {
        this.cusip = cusip;
        this.quantity = quantity;
        this.holder = holder;
        this.tokenForger = tokenForger;
        this.participants = participants;
    }

    public String getCusip() {
        return cusip;
    }

    public Long getQuantity() {
        return quantity;
    }

    public MemberX500Name getHolder() {
        return holder;
    }

    public MemberX500Name getTokenForger() {
        return tokenForger;
    }

    public SecurityToken transfer(MemberX500Name holder,List<PublicKey> participants){
        return new SecurityToken(this.cusip,this.quantity,holder,this.tokenForger,participants);
    }

    @NotNull
    @Override
    public List<PublicKey> getParticipants() {
        return participants;
    }
}
