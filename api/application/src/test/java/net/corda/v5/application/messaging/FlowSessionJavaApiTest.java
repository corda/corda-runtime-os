package net.corda.v5.application.messaging;

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
    public void sendAndReceive() {
        when(flowSession.sendAndReceive(Message.class, message)).thenReturn(message);

        final Message messageTest = flowSession.sendAndReceive(Message.class, message);

        Assertions.assertThat(messageTest).isNotNull();
        Assertions.assertThat(messageTest).isEqualTo(message);
    }

    @Test
    public void receive() {
        when(flowSession.receive(Message.class)).thenReturn(message);

        final Message messageTest = flowSession.receive(Message.class);

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

    static class Message {
        private final String message;

        public Message(String message) {
            this.message = message;
        }
    }
}
