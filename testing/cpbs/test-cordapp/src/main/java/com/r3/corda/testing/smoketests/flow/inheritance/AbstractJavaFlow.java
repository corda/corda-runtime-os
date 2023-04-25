package com.r3.corda.testing.smoketests.flow.inheritance;

import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.membership.MemberLookup;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.MemberInfo;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

public abstract class AbstractJavaFlow implements ClientStartableFlow, JavaMemberResolver {
    private final static Logger logger = LoggerFactory.getLogger(AbstractJavaFlow.class);

    abstract String buildOutput(MemberInfo memberInfo);

    @CordaInject
    public MemberLookup memberLookupService;

    @CordaInject
    public JsonMarshallingService jsonMarshallingService;

    @NotNull
    @Override
    @Suspendable
    public String call(@NotNull ClientRequestBody requestBody) {
        logger.info("Executing Flow...");

        try {
            Map<String, String> request = requestBody.getRequestBodyAsMap(jsonMarshallingService, String.class, String.class);
            String memberInfoRequest = Objects.requireNonNull(request.get("id"), "Failed to find key 'id' in the RPC input args");
            MemberInfo memberInfoResponse = memberLookupService.lookup(MemberX500Name.parse(memberInfoRequest));

            return buildOutput(findMember(memberInfoRequest));
        } catch (Exception exception) {
            logger.error("Unexpected error while processing the flow", exception);
            throw exception;
        }
    }
}
