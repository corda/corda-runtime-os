import {
    FETCH_MESSAGES_AFTER_SEND_FLOW_MS,
    FETCH_SEND_MESSAGE_FLOW_STATUS_POLLING_INTERVAL,
    MESSAGE_SEND_FLOW_POLLING_DELETE_TIMEOUT,
} from '@/constants/timeouts';
import { IconButton, NotificationService, TextInput } from '@r3/r3-tooling-design-system/exports';
import React, { memo, useEffect, useMemo, useRef, useState } from 'react';

import { Id } from '@/models/cpi';
import Message from '../Message/Message';
import { Message as MessageType } from '@/models/Message';
import Messages from '../Messages/Messages';
import { handleFlow } from '@/utils/flowHelpers';
import style from './chat.module.scss';
import useMessagesContext from '@/contexts/messagesContext';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';
import useUserContext from '@/contexts/userContext';

export type MessagePair = { direction: 'outgoing' | 'incoming' | 'outgoing_pending'; content: string };

type Props = {
    selectedParticipants: string[];
    handleSelectReplyParticipant: (participant: string) => void;
    handleOpenParticipantsModal?: () => void;
};

const Chat: React.FC<Props> = ({ handleOpenParticipantsModal, handleSelectReplyParticipant, selectedParticipants }) => {
    const [messages, setMessages] = useState<MessageType[]>([]);
    const [messageValue, setMessageValue] = useState<string>('');
    const [messagesInProgress, setMessagesInProgress] = useState<Map<string, string>>(new Map());

    useEffect(() => {
        setMessagesInProgress(new Map());
    }, [selectedParticipants]);

    const { getChatHistoryForCounterparty, chatHistories, fetchMessages } = useMessagesContext();
    const { vNode, holderShortId, username, password } = useUserContext();

    useEffect(() => {
        if (selectedParticipants.length === 0) {
            setMessages([]);
            return;
        }
        const chatHistory = getChatHistoryForCounterparty(selectedParticipants[0]);
        if (!chatHistory) {
            setMessages([]);
            return;
        }
        setMessages(chatHistory.messages);
    }, [getChatHistoryForCounterparty, JSON.stringify(selectedParticipants), JSON.stringify(chatHistories)]);

    const handleUserTyping = (e: any) => {
        const messageText = e.target.value;
        if (messageText.length > 200) {
            return;
        }
        setMessageValue(e.target.value);
    };

    const removePendingMessageWithRequestId = (clientRequestId: string) => {
        setMessagesInProgress((prev) => {
            const tempMessagesInProgress = new Map(prev);
            if (tempMessagesInProgress.has(clientRequestId)) tempMessagesInProgress.delete(clientRequestId);
            return tempMessagesInProgress;
        });
    };

    const handleMessageSubmit = async () => {
        if (selectedParticipants.length === 0 || !vNode || messageValue.length === 0) return;
        const sender = selectedParticipants[0];
        const clientRequestId = 'sendMessage' + Date.now();
        const messageContent = messageValue;
        setMessageValue('');

        setMessagesInProgress((prev) => {
            const tempMessagesInProgress = new Map(prev);
            tempMessagesInProgress.set(clientRequestId, messageContent);
            return tempMessagesInProgress;
        });

        const pollingInterval = await handleFlow({
            flowType: 'net.cordapp.testing.chat.ChatOutgoingFlow',
            holderShortId: holderShortId,
            clientRequestId: clientRequestId,
            pollIntervalMs: FETCH_SEND_MESSAGE_FLOW_STATUS_POLLING_INTERVAL,
            payload: JSON.stringify({
                recipientX500Name: sender,
                message: messageContent,
            }),
            auth: { username, password },
            onStartFailure: (errorText) => {
                NotificationService.notify(`Failed to start ChatOutgoingFlow ${errorText}`, 'Error', 'danger');
                removePendingMessageWithRequestId(clientRequestId);
                clearInterval(pollingInterval);
            },
            onStatusSuccess: (flowStatus) => {
                // NotificationService.notify(`ChatOutgoingFlow finished, message delivered!`, 'Success', 'success');
                setTimeout(() => {
                    fetchMessages();
                    removePendingMessageWithRequestId(clientRequestId);
                }, FETCH_MESSAGES_AFTER_SEND_FLOW_MS);
                clearInterval(pollingInterval);
            },
            onStatusFailure: (errorText) => {
                NotificationService.notify(`ChatOutgoingFlow failed, error: ${errorText}`, 'Error', 'danger');
                removePendingMessageWithRequestId(clientRequestId);
                clearInterval(pollingInterval);
            },
        });
        // setTimeout(() => {
        //     clearInterval(pollingInterval);
        //     removeMessageWithRequestId(clientRequestId);
        // }, MESSAGE_SEND_FLOW_POLLING_DELETE_TIMEOUT);
    };

    const isChatDisabled = messages.length === 0 && selectedParticipants.length === 0;

    const isMobile = useMobileMediaQuery();

    const allMessages: Map<string, MessagePair> = useMemo(() => {
        const allMessages = new Map<string, MessagePair>();

        messages.forEach((message) => {
            allMessages.set(message.id, { direction: message.direction, content: message.content });
        });

        messagesInProgress.forEach((value, key) => {
            allMessages.set(key, { direction: 'outgoing_pending', content: value });
        });

        return allMessages;
    }, [messages, messagesInProgress]);

    return (
        <div className={style.chat}>
            {isChatDisabled && (
                <div className="p-6">
                    <p className={style.warningText}>{'You have no messages...yet!'}</p>
                    <p className={style.warningText}>{`Please select a participant to send a message to!    ${
                        !isMobile ? ' -->' : ''
                    }`}</p>
                </div>
            )}

            <div className={style.chatContent}>
                {!isChatDisabled && <Messages counterParty={selectedParticipants[0]} messages={allMessages} />}

                {/* TODO: Add bottom margin at keyboard height px value on mobile */}
                <div className={`${style.inputCenter} ${isMobile ? 'shadow-xl' : ''}`}>
                    {handleOpenParticipantsModal && (
                        <IconButton
                            className={`${style.inputCenterButton} ${
                                selectedParticipants.length === 0 ? 'animate-bounce' : ''
                            }`}
                            icon={'AccountMultiplePlus'}
                            size={'small'}
                            variant={'primary'}
                            onClick={handleOpenParticipantsModal}
                        />
                    )}
                    <TextInput
                        disabled={selectedParticipants.length === 0}
                        className={`${handleOpenParticipantsModal ? 'w-4/6' : 'w-5/6'}`}
                        label={
                            selectedParticipants.length === 0
                                ? `${isMobile ? '<-' : ''} Select a participant :)`
                                : messageValue.length === 200
                                ? 'Max 200 chars :('
                                : 'Your message'
                        }
                        value={messageValue}
                        onChange={handleUserTyping}
                        onKeyPress={(event: any) => {
                            if (event.key === 'Enter') {
                                handleMessageSubmit();
                            }
                        }}
                    />
                    <IconButton
                        className={style.inputCenterButton}
                        disabled={selectedParticipants.length === 0 || messageValue.length === 0}
                        size={'small'}
                        variant={'primary'}
                        onClick={handleMessageSubmit}
                        icon={'Send'}
                    />
                </div>
            </div>
        </div>
    );
};

export default memo(Chat);
