package net.corda.v5.application.messaging;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.base.types.MemberX500Name;
import org.jetbrains.annotations.NotNull;

/**
 * KDoc example compilation test
 */
public class FlowMessagingFlowJavaExample implements ClientStartableFlow {
    @CordaInject
    public FlowMessaging flowMessaging;

    @Override
    @NotNull
    public String call(@NotNull ClientRequestBody requestBody) {
        MemberX500Name counterparty = MemberX500Name.parse("CN=Alice, O=Alice Corp, L=LDN, C=GB");
        FlowSession session = flowMessaging.initiateFlow(counterparty);

        String result = session.sendAndReceive(String.class, "hello");

        session.close();

        return result;
    }
}
