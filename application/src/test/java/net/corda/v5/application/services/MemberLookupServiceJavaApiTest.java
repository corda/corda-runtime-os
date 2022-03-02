package net.corda.v5.application.services;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.identity.MemberInfo;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MemberLookupServiceJavaApiTest {

    private final MemberLookupService memberLookupService = mock(MemberLookupService.class);

    private final MemberX500Name bobName = new MemberX500Name("Bob Plc", "Rome", "IT");
    private final MemberInfo memberInfo = mock(MemberInfo.class);
    private final PublicKey bobPublicKey = mock(PublicKey.class);

    @Test
    public void myInfo() {
        when(memberLookupService.myInfo()).thenReturn(memberInfo);

        MemberInfo test = memberLookupService.myInfo();

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupMemberX500Name() {
        when(memberLookupService.lookup(bobName)).thenReturn(memberInfo);

        MemberInfo test = memberLookupService.lookup(bobName);

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupPublicKey() {
        when(memberLookupService.lookup(bobPublicKey)).thenReturn(memberInfo);

        MemberInfo test = memberLookupService.lookup(bobPublicKey);

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupMemberInfo() {
        List<MemberInfo> memberInfos = List.of(memberInfo);
        when(memberLookupService.lookup()).thenReturn(memberInfos);

        List<MemberInfo> test = memberLookupService.lookup();

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfos);
    }
}
