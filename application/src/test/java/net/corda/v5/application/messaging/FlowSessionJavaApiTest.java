package net.corda.v5.application.messaging;

import net.corda.v5.application.messaging.FlowInfo;
import net.corda.v5.base.types.MemberX500Name;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class FlowSessionJavaApiTest {

    private final FlowSession flowSession = mock(FlowSession.class);
    private final Message message = new Message("message");

    @Test
    public void counterparty() {
        final MemberX500Name counterparty = new MemberX500Name("Alice Corp", "LDN", "GB");
        when(flowSession.getCounterparty()).thenReturn(counterparty);

        final MemberX500Name partyTest = flowSession.getCounterparty();

        Assertions.assertThat(partyTest).isNotNull();
        Assertions.assertThat(partyTest).isEqualTo(counterparty);
    }

    @Test
    public void getCounterpartyFlowInfo() {
        final FlowInfo flowInfo = new FlowInfo(5, "corda");
        when(flowSession.getCounterpartyFlowInfo()).thenReturn(flowInfo);

        final FlowInfo flowInfoTest = flowSession.getCounterpartyFlowInfo();

        Assertions.assertThat(flowInfoTest).isNotNull();
        Assertions.assertThat(flowInfoTest).isEqualTo(flowInfo);
    }

    @Test
    public void sendAndReceive() {
        final UntrustworthyData<Message> messageUntrustworthyData = new UntrustworthyData<>(message);
        when(flowSession.sendAndReceive(Message.class, message)).thenReturn(messageUntrustworthyData);

        final Message messageTest = flowSession.sendAndReceive(Message.class, message).unwrap(m -> m);

        Assertions.assertThat(messageTest).isNotNull();
        Assertions.assertThat(messageTest).isEqualTo(message);
    }

    @Test
    public void receive() {
        final UntrustworthyData<Message> messageUntrustworthyData = new UntrustworthyData<>(message);
        when(flowSession.receive(Message.class)).thenReturn(messageUntrustworthyData);

        final Message messageTest = flowSession.receive(Message.class).unwrap(m -> m);

        Assertions.assertThat(messageTest).isNotNull();
        Assertions.assertThat(messageTest).isEqualTo(message);
    }

    @Test
    public void send() {
        flowSession.send(message);

        verify(flowSession, times(1)).send(message);
    }

    @Test
    public void close() {
        flowSession.close();

        verify(flowSession, times(1)).close();
    }

    class Message {
        private String message;

        public Message(String message) {
            this.message = message;
        }
    }
}
