package net.corda.v5.membership;

import net.corda.v5.base.types.MemberX500Name;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MemberInfoJavaApiTest {
    private final MemberInfo memberInfo = mock(MemberInfo.class);

    @Test
    public void getMemberProvidedContext() {
        MemberContext test = mock(MemberContext.class);
        when(memberInfo.getMemberProvidedContext()).thenReturn(test);

        MemberContext result = memberInfo.getMemberProvidedContext();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getMemberProvidedContext();
    }

    @Test
    public void getMgmProvidedContext() {
        MGMContext test = mock(MGMContext.class);
        when(memberInfo.getMgmProvidedContext()).thenReturn(test);

        MGMContext result = memberInfo.getMgmProvidedContext();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getMgmProvidedContext();
    }

    @Test
    public void getName() {
        MemberX500Name testName = MemberX500Name.parse("CN=alice, O=R3, L=Dublin, C=IE");
        when(memberInfo.getName()).thenReturn(testName);

        MemberX500Name result = memberInfo.getName();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(testName);

        verify(memberInfo, times(1)).getName();
    }

    @Test
    public void getLedgerKeys() {
        PublicKey testPublicKey = mock(PublicKey.class);
        List<PublicKey> mockList = List.of(testPublicKey);
        when(memberInfo.getLedgerKeys()).thenReturn(mockList);

        List<PublicKey> result = memberInfo.getLedgerKeys();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(mockList);

        verify(memberInfo, times(1)).getLedgerKeys();
    }

    @Test
    public void getSessionKey() {
        PublicKey testPublicKey = mock(PublicKey.class);
        when(memberInfo.getSessionInitiationKeys()).thenReturn(List.of(testPublicKey));

        var results = memberInfo.getSessionInitiationKeys();

        assertThat(results).contains(testPublicKey);

        verify(memberInfo, times(1)).getSessionInitiationKeys();
    }

    @Test
    public void getPlatformVersion() {
        int test = 5;
        when(memberInfo.getPlatformVersion()).thenReturn(test);

        int result = memberInfo.getPlatformVersion();

        assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getPlatformVersion();
    }

    @Test
    public void getSerial() {
        Long test = 5L;
        when(memberInfo.getSerial()).thenReturn(test);

        Long result = memberInfo.getSerial();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getSerial();
    }

    @Test
    public void isActive() {
        when(memberInfo.isActive()).thenReturn(true);

        Boolean result = memberInfo.isActive();

        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(true);

        verify(memberInfo, times(1)).isActive();
    }
}
