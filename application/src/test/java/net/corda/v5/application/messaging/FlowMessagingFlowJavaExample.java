package net.corda.v5.application.messaging;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.flows.RPCStartableFlow;
import net.corda.v5.base.types.MemberX500Name;

/**
 * KDoc example compilation test
 */
public class FlowMessagingFlowJavaExample implements RPCStartableFlow {
    @CordaInject
    public FlowMessaging flowMessaging;

    @Override
    public String call(RPCRequestData requestBody) {
        MemberX500Name counterparty = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB");
        FlowSession session = flowMessaging.initiateFlow(counterparty);

        String result = session.sendAndReceive(String.class, "hello");

        session.close();

        return result;
    }
}
