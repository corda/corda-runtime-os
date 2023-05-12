package com.r3.corda.testing.smoketests.flow.inheritance;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.MemberInfo;

@SuppressWarnings("unused")
public class DependencyInjectionTestJavaFlow extends AbstractJavaFlow {

    @Override
    String buildOutput(MemberInfo memberInfo) {
        return memberInfo != null ? memberInfo.getName().toString() : "Failed to find MemberInfo";
    }

    @Override
    public MemberInfo findMember(String memberX500Name) {
        return memberLookupService.lookup(MemberX500Name.parse(memberX500Name));
    }
}
