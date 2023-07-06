package com.r3.corda.testing.smoketests.ion.workflows;

import net.corda.v5.base.types.MemberX500Name;

import java.util.UUID;

public class BilateralArgs {
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

    public BilateralArgs() {
    }

    public BilateralArgs(String tradeStatus, Long shrQty, String securityID, double settlementAmt, MemberX500Name deliver, MemberX500Name receiver, String settlementDelivererId, String settlementReceiverID, MemberX500Name dtcc, MemberX500Name dtccObserver, String setlmntCrncyCd, String sttlmentlnsDlvrrRefId, UUID linearId) {
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
    }

    public UUID getLinearId() {
        return linearId;
    }

    public String getTradeStatus() {
        return tradeStatus;
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
}
