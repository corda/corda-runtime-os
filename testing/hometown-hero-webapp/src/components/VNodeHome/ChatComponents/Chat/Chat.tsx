import { IconButton, NotificationService, TextInput } from '@r3/r3-tooling-design-system/exports';
import React, { useCallback, useEffect, useRef, useState } from 'react';

import Message from '../Message/Message';
import { Message as MessageType } from '@/models/Message';
import { TEMP_MESSAGES } from '@/tempData/tempMessages';
import { TEMP_USER_500 } from '@/tempData/user';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';
import style from './chat.module.scss';
import { useMobileMediaQuery } from '@/hooks/useMediaQueries';

type Props = {
    selectedParticipants: string[];
    handleSelectReplyParticipant: (participant: string) => void;
    handleOpenParticipantsModal?: () => void;
};

const Chat: React.FC<Props> = ({ handleOpenParticipantsModal, handleSelectReplyParticipant, selectedParticipants }) => {
    const [messages, setMessages] = useState<MessageType[]>([]);
    const [messageValue, setMessageValue] = useState<string>('');

    const messagesEndRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!messagesEndRef.current) return;
        messagesEndRef.current.scrollIntoView();
    }, [messages]);

    // TODO: Set the actual x500, probably in UserContext when this data is available
    const currentUserX500 = TEMP_USER_500;

    const fetchMessages = useCallback(async () => {
        const response = await apiCall({ method: 'get', path: '/api/messages', axiosInstance: axiosInstance });
        if (response.error) {
            NotificationService.notify(`Failed to fetch messages: Error: ${response.error}`, 'Error', 'danger');
        } else {
            // TODO: Set the messages here from api response data
        }
    }, []);

    useEffect(() => {
        fetchMessages();

        // TODO: Remove temp data of messages
        setMessages(TEMP_MESSAGES);

        // TODO: Set interval of polling messages if there will be no web socket implementation available
    }, [fetchMessages]);

    const handleUserTyping = (e: any) => {
        setMessageValue(e.target.value);
    };

    const handleMessageSubmit = async () => {
        // TODO: adjust to api spec
        const response = await apiCall({
            method: 'post',
            path: '/api/sendMessage',
            params: { messageContent: messageValue },
            axiosInstance: axiosInstance,
        });
        if (response.error) {
            NotificationService.notify(`Failed to send message: Error: ${response.error}`, 'Error', 'danger');
        } else {
            fetchMessages();
        }
        setMessageValue('');
    };

    const isChatDisabled = messages.length === 0 && selectedParticipants.length === 0;

    const isMobile = useMobileMediaQuery();

    return (
        <div className={style.chat}>
            {isChatDisabled && (
                <>
                    <p className={style.warningText}>{'You have no messages...yet!'}</p>
                    <p className={style.warningText}>{'Please select a participant(s) to send a message to!    -->'}</p>
                </>
            )}

            {!isChatDisabled && (
                <div className={style.chatContent}>
                    <div className={style.messagesList}>
                        {messages.map((message, index) => {
                            const isMyMessage = message.x500name === currentUserX500;
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
                                    ? `${isMobile ? '<-' : ''} Select participant(s)`
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
            )}
        </div>
    );
};

export default Chat;
