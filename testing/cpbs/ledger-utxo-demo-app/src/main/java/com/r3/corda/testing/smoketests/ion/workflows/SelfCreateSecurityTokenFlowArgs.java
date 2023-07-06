package com.r3.corda.testing.smoketests.ion.workflows;

import net.corda.v5.base.types.MemberX500Name;

public class SelfCreateSecurityTokenFlowArgs {

    private String cusip;
    private Long quantity;
    private MemberX500Name holder;
    private MemberX500Name tokenForger;

    public SelfCreateSecurityTokenFlowArgs() {
    }

    public SelfCreateSecurityTokenFlowArgs(String cusip, Long quantity, MemberX500Name holder, MemberX500Name tokenForger) {
        this.cusip = cusip;
        this.quantity = quantity;
        this.holder = holder;
        this.tokenForger = tokenForger;
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
}
