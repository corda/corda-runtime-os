package com.r3.corda.testing.smoketests.ion.states;

import com.r3.corda.testing.smoketests.ion.contracts.BilateralContract;
import net.corda.v5.base.annotations.ConstructorForDeserialization;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.ledger.utxo.BelongsToContract;
import net.corda.v5.ledger.utxo.ContractState;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;
import java.util.UUID;

@BelongsToContract(BilateralContract.class)
public class BilateralState implements ContractState {

    private String tradeStatus;
    private Long shrQty;
    private String securityID;
    private double settlementAmt;
    private MemberX500Name deliver;
    private MemberX500Name receiver;
    private String settlementDelivererId;
    private String settlementReceiverID;
    private MemberX500Name dtcc;
    private MemberX500Name dtccObserver;
    private String setlmntCrncyCd;
    private String sttlmentlnsDlvrrRefId;
    private UUID linearId;
    public List<PublicKey> participants; //Need to have deliverer, receiver, dtcc and dtccObserver

    @ConstructorForDeserialization
    public BilateralState(String tradeStatus, Long shrQty, String securityID, double settlementAmt, MemberX500Name deliver, MemberX500Name receiver, String settlementDelivererId, String settlementReceiverID, MemberX500Name dtcc, MemberX500Name dtccObserver, String setlmntCrncyCd, String sttlmentlnsDlvrrRefId, UUID linearId, List<PublicKey> participants) {
        this.tradeStatus = tradeStatus;
        this.shrQty = shrQty;
        this.securityID = securityID;
        this.settlementAmt = settlementAmt;
        this.deliver = deliver;
        this.receiver = receiver;
        this.settlementDelivererId = settlementDelivererId;
        this.settlementReceiverID = settlementReceiverID;
        this.dtcc = dtcc;
        this.dtccObserver = dtccObserver;
        this.setlmntCrncyCd = setlmntCrncyCd;
        this.sttlmentlnsDlvrrRefId = sttlmentlnsDlvrrRefId;
        this.linearId = linearId;
        this.participants = participants;
    }

    public Long getShrQty() {
        return shrQty;
    }

    public String getSecurityID() {
        return securityID;
    }

    public double getSettlementAmt() {
        return settlementAmt;
    }

    public MemberX500Name getDeliver() {
        return deliver;
    }

    public MemberX500Name getReceiver() {
        return receiver;
    }

    public String getSettlementDelivererId() {
        return settlementDelivererId;
    }

    public String getSettlementReceiverID() {
        return settlementReceiverID;
    }

    public MemberX500Name getDtcc() {
        return dtcc;
    }

    public MemberX500Name getDtccObserver() {
        return dtccObserver;
    }

    public String getSetlmntCrncyCd() {
        return setlmntCrncyCd;
    }

    public String getSttlmentlnsDlvrrRefId() {
        return sttlmentlnsDlvrrRefId;
    }

    public UUID getLinearId() {
        return linearId;
    }

    public String getTradeStatus() {
        return tradeStatus;
    }

    @NotNull
    @Override
    public List<PublicKey> getParticipants() {
        return participants;
    }

    public BilateralState update(String tradeStatus, Long shrQty, String securityID, double settlementAmt, String setlmntCrncyCd, String sttlmentlnsDlvrrRefId) {
        return new BilateralState(tradeStatus,shrQty,securityID,settlementAmt,this.deliver,this.receiver,this.settlementDelivererId,this.settlementReceiverID,
                this.dtcc,this.dtccObserver,setlmntCrncyCd,sttlmentlnsDlvrrRefId, this.linearId,this.participants );
    }
}
