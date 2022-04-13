package net.corda.v5.ledger.notary;


import net.corda.v5.application.flows.Flow;
import net.corda.v5.application.messaging.FlowSession;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NotaryServiceJavaApiTest {

    private final NotaryService notaryService = mock(NotaryService.class);
    private final PublicKey publicKey = mock(PublicKey.class);
    private final FlowSession flowSession = mock(FlowSession.class);
    private final TestClass testClass = new TestClass();
    private final NotaryService.Query.Request request = mock(NotaryService.Query.Request.class);
    private final NotaryService.Query.Result result = mock(NotaryService.Query.Result.class);

    @Test
    public void getNotaryIdentityKey() {
        when(notaryService.getNotaryIdentityKey()).thenReturn(publicKey);

        PublicKey result = notaryService.getNotaryIdentityKey();

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(publicKey);
    }

    @Test
    public void createServiceFlow() {
        when(notaryService.createServiceFlow(flowSession)).thenReturn(testClass);

        Flow<Void> result = notaryService.createServiceFlow(flowSession);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testClass);
    }

    @Test
    public void processQuery() {
        when(notaryService.processQuery(request)).thenReturn(result);

        NotaryService.Query.Result result = notaryService.processQuery(request);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(result);
    }

    @Test
    public void start() {
        notaryService.start();

        verify(notaryService, times(1)).start();
    }

    @Test
    public void stop() {
        notaryService.stop();

        verify(notaryService, times(1)).stop();
    }

    static class TestClass implements Flow<Void> {

        @Override
        public Void call() {
            return null;
        }
    }
}
