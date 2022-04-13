package net.corda.v5.application.configuration;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CordappProviderJavaApiTest {

    private final CordappConfigProvider cordappConfigProvider = mock(CordappConfigProvider.class);
    private final CordappConfig cordappConfig = mock(CordappConfig.class);

    @Test
    public void getAppConfig() {
        when(cordappConfigProvider.getAppConfig()).thenReturn(cordappConfig);

        CordappConfig result = cordappConfigProvider.getAppConfig();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(cordappConfig);
    }
}
