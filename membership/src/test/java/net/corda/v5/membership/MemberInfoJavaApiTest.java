package net.corda.v5.membership;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;
import java.util.List;

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

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getMemberProvidedContext();
    }

    @Test
    public void getMgmProvidedContext() {
        MGMContext test = mock(MGMContext.class);
        when(memberInfo.getMgmProvidedContext()).thenReturn(test);

        MGMContext result = memberInfo.getMgmProvidedContext();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getMgmProvidedContext();
    }

    @Test
    public void getName() {
        MemberX500Name testName = MemberX500Name.parse("CN=alice, O=R3, L=Dublin, C=IE");
        when(memberInfo.getName()).thenReturn(testName);

        MemberX500Name result = memberInfo.getName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testName);

        verify(memberInfo, times(1)).getName();
    }

    @Test
    public void getIdentityKeys() {
        PublicKey testPublicKey = mock(PublicKey.class);
        List<PublicKey> mockList = List.of(testPublicKey);
        when(memberInfo.getIdentityKeys()).thenReturn(mockList);

        List<PublicKey> result = memberInfo.getIdentityKeys();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockList);

        verify(memberInfo, times(1)).getIdentityKeys();
    }

    @Test
    public void getPlatformVersion() {
        int test = 5;
        when(memberInfo.getPlatformVersion()).thenReturn(test);

        int result = memberInfo.getPlatformVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getPlatformVersion();
    }

    @Test
    public void getSerial() {
        Long test = 5L;
        when(memberInfo.getSerial()).thenReturn(test);

        Long result = memberInfo.getSerial();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(test);

        verify(memberInfo, times(1)).getSerial();
    }

    @Test
    public void isActive() {
        when(memberInfo.isActive()).thenReturn(true);

        Boolean result = memberInfo.isActive();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(true);

        verify(memberInfo, times(1)).isActive();
    }
}
