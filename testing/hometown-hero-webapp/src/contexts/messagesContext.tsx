import React, { useEffect } from 'react';

import { NotificationService } from '@r3/r3-tooling-design-system/exports';
import createCtx from './createCtx';
import { handleFlow } from '@/utils/flowHelpers';
import { requestStartFlow } from '@/api/flows';
import { useLocalStorage } from '@/hooks/useLocalStorage';
import useUserContext from './userContext';

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
    chatHistories: ChatHistory[];
    getChatHistoryForSender: (sender: string) => ChatHistory | undefined;
};

const [useMessagesContext, Provider] = createCtx<MessagesContextProps>();
export default useMessagesContext;

export const MessagesContextProvider: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
    const { username, password, holderShortId } = useUserContext();
    const [chatHistories, setChatHistories] = useLocalStorage<ChatHistory[]>('chatHistory', []);

    const getChatHistoryForSender = (sender: string): ChatHistory | undefined => {
        return chatHistories.find((chatHistory) => chatHistory.sender === sender);
    };

    const handleAppendMessagesToChatHistories = (
        chatHistories: ChatHistory[],
        newMessages: Messages
    ): ChatHistory[] => {
        const senderKeys = new Set(newMessages.messages.map((message) => message.sender));
        const tempChatHistories = [...chatHistories];
        senderKeys.forEach((sender) => {
            const newMessagesFromSender = newMessages.messages.filter((message) => message.sender === sender);
            const existingChatHistory = tempChatHistories.find((cH) => cH.sender === 'sender');
            if (!existingChatHistory) {
                tempChatHistories.push({
                    sender: sender,
                    messages: newMessagesFromSender,
                });
                return;
            }
            existingChatHistory.messages = [...existingChatHistory.messages, ...newMessagesFromSender];
        });
        return tempChatHistories;
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
                    setChatHistories((prev) => handleAppendMessagesToChatHistories(prev, messages));
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

            setTimeout(() => {
                clearInterval(flowStatusInterval);
            }, 5000);
        };
        fetchMessages();
        // setInterval(() => {
        //     fetchMessages();
        // }, 2000);
    }, [holderShortId]);

    return <Provider value={{ chatHistories, getChatHistoryForSender }}>{children}</Provider>;
};
