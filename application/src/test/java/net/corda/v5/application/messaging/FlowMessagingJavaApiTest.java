package net.corda.v5.application.messaging;

import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlowMessagingJavaApiTest {

    private final FlowMessaging flowMessaging = mock(FlowMessaging.class);
    private final FlowSession flowSession = mock(FlowSession.class);

    @Test
    public void initiateFlowParty() {
        final MemberX500Name counterparty = new MemberX500Name("Alice Corp", "LDN", "GB");
        when(flowMessaging.initiateFlow(counterparty)).thenReturn(flowSession);

        FlowSession result = flowMessaging.initiateFlow(counterparty);

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(flowSession);
    }

    @Test
    public void receiveAllMap() {
        UntrustworthyData<String> untrustworthyData = new UntrustworthyData<>("data sent from other party");
        Map<FlowSession, UntrustworthyData<String>> mockedResult = Map.of(flowSession, untrustworthyData);

        doReturn(mockedResult).when(flowMessaging).receiveAllMap(Map.of(flowSession, String.class));

        Map<FlowSession, UntrustworthyData<Object>> result = flowMessaging.receiveAllMap(Map.of(flowSession, String.class));
        List<String> collected = result.values().stream()
                .map(data -> data.unwrap(s -> {
                    if (!(s instanceof String)) {
                        throw new IllegalArgumentException("Received the wrong payload from counter party");
                    }
                    return (String) s;
                })).collect(Collectors.toList());

        Assertions.assertThat(collected.get(0)).isEqualTo("data sent from other party");
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(mockedResult);
    }

    @Test
    public void receiveAll() {
        List<UntrustworthyData<Object>> testList = List.of(new UntrustworthyData<>(new Object()));

        doReturn(testList).when(flowMessaging).receiveAll(Object.class, Set.of(flowSession));

        List<UntrustworthyData<Object>> result = flowMessaging.receiveAll(Object.class, Set.of(flowSession));

        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(result).isEqualTo(testList);
    }

    @Test
    public void sendAll() {
        Object obj = new Object();
        flowMessaging.sendAll(obj, Set.of(flowSession));
        verify(flowMessaging, times(1)).sendAll(obj, Set.of(flowSession));
    }

    @Test
    public void sendAllMap() {
        Object obj = new Object();
        flowMessaging.sendAllMap(Map.of(flowSession, obj));
        verify(flowMessaging, times(1)).sendAllMap(Map.of(flowSession, obj));
    }

    @Test
    public void close() {
        flowMessaging.close(Set.of(flowSession));
        verify(flowMessaging, times(1)).close(Set.of(flowSession));
    }
}
