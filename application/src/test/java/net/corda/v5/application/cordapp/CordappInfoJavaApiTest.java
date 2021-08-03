package net.corda.v5.application.cordapp;

import net.corda.v5.crypto.SecureHash;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CordappInfoJavaApiTest {

    private final CordappInfo cordappInfo = mock(CordappInfo.class);

    @Test
    public void getType() {
        when(cordappInfo.getType()).thenReturn("type");

        String result = cordappInfo.getType();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("type");
    }

    @Test
    public void getName() {
        when(cordappInfo.getName()).thenReturn("name");

        String result = cordappInfo.getName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("name");
    }

    @Test
    public void getShortName() {
        when(cordappInfo.getShortName()).thenReturn("shortName");

        String result = cordappInfo.getShortName();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("shortName");
    }

    @Test
    public void getVersion() {
        when(cordappInfo.getVersion()).thenReturn("version");

        String result = cordappInfo.getVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("version");
    }
    @Test
    public void getVendor() {
        when(cordappInfo.getVendor()).thenReturn("vendor");

        String result = cordappInfo.getVendor();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("vendor");
    }

    @Test
    public void getLicence() {
        when(cordappInfo.getLicence()).thenReturn("licence");

        String result = cordappInfo.getLicence();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo("licence");
    }

    @Test
    public void getMinimumPlatformVersion() {
        when(cordappInfo.getMinimumPlatformVersion()).thenReturn(5);

        int result = cordappInfo.getMinimumPlatformVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(5);
    }

    @Test
    public void getTargetPlatformVersion() {
        when(cordappInfo.getTargetPlatformVersion()).thenReturn(5);

        int result = cordappInfo.getTargetPlatformVersion();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(5);
    }

    @Test
    public void getJarHash() {
        SecureHash secureHash256 = SecureHash.create("SHA-256:0123456789ABCDE1");
        when(cordappInfo.getJarHash()).thenReturn(secureHash256);

        SecureHash result = cordappInfo.getJarHash();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(secureHash256);
    }
}
