package com.r3.corda.testing.smoketests.flow.inheritance;

import net.corda.v5.membership.MemberInfo;

public interface JavaMemberResolver {
    MemberInfo findMember(String memberX500Name);
}
