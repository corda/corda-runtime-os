package net.cordapp.testing.chat;

import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.flows.RPCRequestData;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.messaging.FlowMessaging;
import net.corda.v5.application.messaging.UntrustworthyData;
import net.corda.v5.base.types.MemberX500Name;
import net.cordapp.testing.chatframework.FlowMockHelper;
import net.cordapp.testing.chatframework.FlowMockMessageLink;
import net.cordapp.testing.chatframework.InjectableMockServices;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static net.cordapp.testing.chat.FlowTestUtilsKt.executeConcurrently;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChatFlowCollaborationTestsJava {
    static final String RECIPIENT_X500_NAME = "CN=Bob, O=R3, L=London, C=GB";
    static final String FROM_X500_NAME = "CN=Alice, O=R3, L=London, C=GB";
    static final String MESSAGE = "chat message";

    static final String DUMMY_FLOW_RETURN = "dummy_flow_return";

    static final FlowMockHelper outgoingFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowMessaging.class)
                    .createMockService(JsonMarshallingService.class)
                    .createMockService(FlowEngine.class));
    static final FlowMockHelper incomingFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowEngine.class));

    static final FlowMockHelper readerFlowMockHelper = FlowMockHelper.fromInjectableServices(
            new InjectableMockServices()
                    .createMockService(FlowEngine.class)
                    .createMockService(JsonMarshallingService.class));

    static final ChatOutgoingFlow outgoingChatFlow = outgoingFlowMockHelper.createFlow(ChatOutgoingFlow.class);
    static final ChatIncomingFlow incomingChatFlow = incomingFlowMockHelper.createFlow(ChatIncomingFlow.class);
    static final ChatReaderFlow readerChatFlow = readerFlowMockHelper.createFlow(ChatReaderFlow.class);

    @BeforeAll
    void setup() {
        FlowEngine outgoingMock = (FlowEngine) outgoingFlowMockHelper.getMockService(FlowEngine.class);
        when(outgoingMock.getVirtualNodeName()).thenReturn(MemberX500Name.parse(FROM_X500_NAME));

        FlowEngine incomingMock = (FlowEngine) incomingFlowMockHelper.getMockService(FlowEngine.class);
        when(incomingMock.getVirtualNodeName()).thenReturn(MemberX500Name.parse(RECIPIENT_X500_NAME));
    }

    @Test
    void FlowSendsMessage() {
        FlowMockMessageLink messageLink = new FlowMockMessageLink(outgoingFlowMockHelper, incomingFlowMockHelper);

        when(messageLink.getToFlowSession().receive(MessageContainer.class)).thenAnswer(
                (Answer<UntrustworthyData<MessageContainer>>) invocation ->
                        new UntrustworthyData(messageLink.messageQueue.getOrWaitForNextMessage())
        );

        RPCRequestData requestData = mock(RPCRequestData.class);
        when(requestData.getRequestBodyAs(
                (JsonMarshallingService) outgoingFlowMockHelper.getMockService(JsonMarshallingService.class),
                OutgoingChatMessage.class)
        ).thenReturn(new OutgoingChatMessage(RECIPIENT_X500_NAME, MESSAGE));

        executeConcurrently(() -> {
                    outgoingChatFlow.call(requestData);
                },
                () -> {
                    incomingChatFlow.call(messageLink.toFlowSession);
                });

        messageLink.failIfPendingMessages();

        List<IncomingChatMessage> messageList = new ArrayList<IncomingChatMessage>();
        messageList.add(new IncomingChatMessage(FROM_X500_NAME, MESSAGE));
        ReceivedChatMessages expectedMessages = new ReceivedChatMessages(messageList);

        when(((JsonMarshallingService) readerFlowMockHelper.getMockService(JsonMarshallingService.class))
                .format(expectedMessages)).thenReturn(DUMMY_FLOW_RETURN
        );

        String messagesJson = readerChatFlow.call(mock(RPCRequestData.class)); // Parameter not used by Flow
        assertThat(messagesJson).isEqualTo(DUMMY_FLOW_RETURN);
    }
}
