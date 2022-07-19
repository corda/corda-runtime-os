import React, { useEffect } from 'react';

import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import createCtx from './createCtx';
import { handleFlow } from '@/utils/flowHelpers';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import useUserContext from './userContext';

export type UsersChatHistory = {
    username: string;
    chatHistories: ChatHistory[];
};

export type ChatHistory = {
    sender: string;
    messages: Message[];
};

export type Messages = {
    messages: Message[];
};

export type Message = {
    sender: string;
    message: string;
};

type MessagesContextProps = {
    userChatHistory: UsersChatHistory;
    addMessageToChatHistoryForSender: (sender: string, message: Message) => void;
    getChatHistoryForSender: (sender: string) => ChatHistory | undefined;
    getTotalIncomingMessagesForSender: (sender: string) => number;
};

const [useMessagesContext, Provider] = createCtx<MessagesContextProps>();
export default useMessagesContext;

export const MessagesContextProvider: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
    const { username, password, holderShortId } = useUserContext();
    const [userChatHistory, setUserChatHistory] = useLocalStorage<UsersChatHistory>(`${username}chatHistory`, {
        username: username,
        chatHistories: [],
    });

    const getChatHistoryForSender = (sender: string): ChatHistory | undefined => {
        return userChatHistory.chatHistories.find((chatHistory) => chatHistory.sender === sender);
    };

    const getTotalIncomingMessagesForSender = (sender: string): number => {
        const chatHistory = getChatHistoryForSender(sender);

        if (!chatHistory) return 0;

        return chatHistory.messages.filter((message) => message.sender === sender).length;
    };

    const addMessageToChatHistoryForSender = (sender: string, message: Message) => {
        const tempUserChatHistory = { ...userChatHistory };
        const existingChatHistory = tempUserChatHistory.chatHistories.find((cH) => cH.sender === sender);
        if (!existingChatHistory) {
            tempUserChatHistory.chatHistories.push({
                sender: sender,
                messages: [message],
            });
            setUserChatHistory(tempUserChatHistory);
            return;
        }
        existingChatHistory.messages = [...existingChatHistory.messages, message];
        setUserChatHistory(tempUserChatHistory);
    };

    const handleAppendMessagesToChatHistories = (
        userChatHistory: UsersChatHistory,
        newMessages: Messages
    ): UsersChatHistory => {
        const senderKeys = new Set(newMessages.messages.map((message) => message.sender));
        const tempUserChatHistory = { ...userChatHistory };
        senderKeys.forEach((sender) => {
            const newMessagesFromSender = newMessages.messages.filter((message) => message.sender === sender);
            const existingChatHistory = tempUserChatHistory.chatHistories.find((cH) => cH.sender === sender);
            if (!existingChatHistory) {
                tempUserChatHistory.chatHistories.push({
                    sender: sender,
                    messages: newMessagesFromSender,
                });
                return;
            }
            console.log('HANDLE APPEND MESAGES', newMessages);
            existingChatHistory.messages = [...existingChatHistory.messages, ...newMessagesFromSender];
        });
        return tempUserChatHistory;
    };

    useEffect(() => {
        if (holderShortId.length === 0) return;

        const fetchMessages = async () => {
            const clientRequestId = 'fetchMessage' + Date.now();
            const flowStatusInterval = await handleFlow({
                holderShortId,
                clientRequestId,
                flowType: 'net.cordapp.testing.chat.ChatReaderFlow',
                payload: JSON.stringify({}),
                onStartFailure: (errorText) => {
                    NotificationService.notify(
                        `Error starting flow with clientRequestId: ${clientRequestId}.. Error: ${errorText}`,
                        'error',
                        'danger'
                    );
                },
                onStatusSuccess: (flowResult) => {
                    const messages = JSON.parse(flowResult) as Messages;
                    if (messages.messages.length > 0) {
                        setUserChatHistory((prev) => handleAppendMessagesToChatHistories(prev, messages));
                    }
                },
                onStatusFailure: (errorText) => {
                    NotificationService.notify(
                        `Error polling flow status with clientRequestId: ${clientRequestId}.. Error: ${errorText}`,
                        'error',
                        'danger'
                    );
                },
                auth: { username, password },
            });

            // setTimeout(() => {
            //     clearInterval(flowStatusInterval);
            // }, 5000);
        };
        //fetchMessages();
        setInterval(() => {
            fetchMessages();
        }, 1000);
    }, [holderShortId]);

    return (
        <Provider
            value={{
                userChatHistory,
                addMessageToChatHistoryForSender,
                getChatHistoryForSender,
                getTotalIncomingMessagesForSender,
            }}
        >
            {children}
        </Provider>
    );
};
