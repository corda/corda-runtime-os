package net.corda.v5.application.services;

import net.corda.v5.base.types.MemberX500Name;
import net.corda.v5.membership.MemberInfo;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MemberLookupJavaApiTest {

    private final MemberLookup memberLookup = mock(MemberLookup.class);

    private final MemberX500Name bobName = new MemberX500Name("Bob Plc", "Rome", "IT");
    private final MemberInfo memberInfo = mock(MemberInfo.class);
    private final PublicKey bobPublicKey = mock(PublicKey.class);

    @Test
    public void myInfo() {
        when(memberLookup.myInfo()).thenReturn(memberInfo);

        MemberInfo test = memberLookup.myInfo();

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupMemberX500Name() {
        when(memberLookup.lookup(bobName)).thenReturn(memberInfo);

        MemberInfo test = memberLookup.lookup(bobName);

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupPublicKey() {
        when(memberLookup.lookup(bobPublicKey)).thenReturn(memberInfo);

        MemberInfo test = memberLookup.lookup(bobPublicKey);

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfo);
    }

    @Test
    public void lookupMemberInfo() {
        List<MemberInfo> memberInfos = List.of(memberInfo);
        when(memberLookup.lookup()).thenReturn(memberInfos);

        List<MemberInfo> test = memberLookup.lookup();

        Assertions.assertThat(test).isNotNull();
        Assertions.assertThat(test).isEqualTo(memberInfos);
    }
}
