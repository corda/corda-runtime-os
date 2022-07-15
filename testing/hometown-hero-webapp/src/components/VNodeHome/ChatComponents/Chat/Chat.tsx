import { IconButton, NotificationService, TextInput } from '@r3/r3-tooling-design-system/exports';
import React, { useEffect, useMemo, useRef, useState } from 'react';

import Message from '../Message/Message';
import { Message as MessageType } from '@/models/Message';
import { handleFlow } from '@/utils/flowHelpers';
import style from './chat.module.scss';
import useMessagesContext from '@/contexts/messagesContext';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';
import useUserContext from '@/contexts/userContext';

type Props = {
    selectedParticipants: string[];
    handleSelectReplyParticipant: (participant: string) => void;
    handleOpenParticipantsModal?: () => void;
};

const Chat: React.FC<Props> = ({ handleOpenParticipantsModal, handleSelectReplyParticipant, selectedParticipants }) => {
    const [messages, setMessages] = useState<MessageType[]>([]);
    const [messageValue, setMessageValue] = useState<string>('');

    const { getChatHistoryForSender, addMessageToChatHistoryForSender, userChatHistory } = useMessagesContext();
    const { vNode, holderShortId, username, password } = useUserContext();

    const messagesEndRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (selectedParticipants.length === 0) {
            setMessages([]);
            return;
        }
        const chatHistory = getChatHistoryForSender(selectedParticipants[0]);
        if (!chatHistory) {
            setMessages([]);
            return;
        }
        setMessages(chatHistory.messages.map((message) => ({ x500name: message.sender, message: message.message })));
    }, [getChatHistoryForSender, selectedParticipants, JSON.stringify(userChatHistory)]);

    useEffect(() => {
        if (!messagesEndRef.current) return;
        messagesEndRef.current.scrollIntoView();
    }, [messages]);

    const handleUserTyping = (e: any) => {
        const messageText = e.target.value;
        if (messageText.length > 200) {
            return;
        }
        setMessageValue(e.target.value);
    };

    const handleMessageSubmit = async () => {
        if (selectedParticipants.length === 0 || !vNode) return;
        const sender = selectedParticipants[0];
        const clientRequestId = 'sendMessage' + Date.now();
        const pollingInterval = await handleFlow({
            flowType: 'net.cordapp.testing.chat.ChatOutgoingFlow',
            holderShortId: holderShortId,
            clientRequestId: clientRequestId,
            payload: JSON.stringify({
                recipientX500Name: sender,
                message: messageValue,
            }),
            auth: { username, password },
            onStartFailure: (errorText) => {
                NotificationService.notify(`Failed to start ChatOutgoingFlow ${errorText}`, 'Error', 'danger');
            },
            onStatusSuccess: (flowStatus) => {
                addMessageToChatHistoryForSender(sender, {
                    sender: vNode.holdingIdentity.x500Name,
                    message: messageValue,
                });
                NotificationService.notify(`ChatOutgoingFlow finished, message delivered!`, 'Success', 'success');
            },
            onStatusFailure: (errorText) => {
                NotificationService.notify(`ChatOutgoingFlow failed, error: ${errorText}`, 'Error', 'danger');
            },
        });
        setMessageValue('');
    };

    const isChatDisabled = messages.length === 0 && selectedParticipants.length === 0;

    const isMobile = useMobileMediaQuery();

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
                {!isChatDisabled && (
                    <div className={style.messagesList}>
                        {messages.map((message, index) => {
                            const isMyMessage = message.x500name === vNode?.holdingIdentity.x500Name;
                            return (
                                <Message
                                    key={index}
                                    message={message}
                                    isMyMessage={isMyMessage}
                                    selectReplyParticipant={handleSelectReplyParticipant}
                                />
                            );
                        })}
                        <div ref={messagesEndRef} />
                    </div>
                )}

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

export default Chat;
