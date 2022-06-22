import { Button, NotificationService, TextInput } from '@r3/r3-tooling-design-system/exports';
import React, { useCallback, useEffect, useState } from 'react';

import { Message } from '@/models/Message';
import { TEMP_MESSAGES } from '@/tempData/tempMessages';
import { TEMP_USER_500 } from '@/tempData/user';
import apiCall from '@/api/apiCall';
import { axiosInstance } from '@/api/axiosConfig';

type Props = {
    selectedParticipants: string[];
};

const Chat: React.FC<Props> = ({ selectedParticipants }) => {
    const [messages, setMessages] = useState<Message[]>([]);
    const [messageValue, setMessageValue] = useState<string>('');

    const currentUserX500 = TEMP_USER_500;

    const fetchMessages = useCallback(async () => {
        const response = await apiCall({ method: 'get', path: '/api/messages', axiosInstance: axiosInstance });
        if (response.error) {
            NotificationService.notify(`Failed to fetch messages: Error: ${response.error}`, 'Error', 'danger');
        } else {
            //Set participants here
        }
    }, []);

    useEffect(() => {
        //fetchMessages();
        setMessages(TEMP_MESSAGES);

        // const interval = setInterval(() => {
        //     fetchMessages();
        // }, 2000);
        // return () => {
        //     clearInterval(interval);
        // };
    }, [fetchMessages]);

    const handleUserTyping = (e: any) => {
        setMessageValue(e.target.value);
    };

    const handleMessageSubmit = async () => {
        //Do some send message call here
        const response = await apiCall({
            method: 'post',
            path: '/api/sendMessage',
            params: { messageContent: messageValue },
            axiosInstance: axiosInstance,
        });
        if (response.error) {
            NotificationService.notify(`Failed to fetch messages: Error: ${response.error}`, 'Error', 'danger');
        } else {
            fetchMessages();
        }

        setMessageValue('');
    };

    const isChatDisabled = messages.length === 0 && selectedParticipants.length === 0;

    return (
        <div style={{ width: 400, height: 550 }}>
            {isChatDisabled && (
                <>
                    <p className="mt-8 opacity-75 text-3xl">{'You have no messages...yet!'}</p>
                    <p className="mt-8 opacity-75 text-3xl">
                        {'Please select a participant(s) to send a message to!    -->'}
                    </p>
                </>
            )}

            {!isChatDisabled && (
                <div className="h-full flex flex-col gap-6 pt-6 ">
                    <div className="border border-light-gray border-opacity-25 overflow-y-scroll p4">
                        {messages.map((message) => {
                            const myMessage = message.x500name === currentUserX500;
                            return (
                                <div className={`m-2 ${myMessage ? 'ml-auto' : 'mr-auto'}`} style={{ maxWidth: '70%' }}>
                                    <p className={`text-xs ${myMessage ? 'font-bold' : 'font-semibold opacity-50'}`}>
                                        {myMessage ? 'Me' : `${message.x500name.substring(0, 40)}...`}
                                    </p>
                                    <div
                                        className={`mt-0 rounded-xl border border-blue shadow-md p-4 ${
                                            myMessage ? 'bg-blue-100' : ''
                                        }`}
                                    >
                                        <p className="leading-5">{message.message}</p>
                                    </div>
                                </div>
                            );
                        })}
                    </div>

                    <div className="flex justify-center align-center gap-4">
                        <TextInput
                            disabled={selectedParticipants.length === 0}
                            className="w-4/5"
                            label={selectedParticipants.length === 0 ? 'Please select participant' : 'Your message'}
                            value={messageValue}
                            onChange={handleUserTyping}
                        />
                        <Button
                            className="shadow-lg"
                            disabled={selectedParticipants.length === 0 || messageValue.length === 0}
                            size={'small'}
                            variant={'primary'}
                            onClick={handleMessageSubmit}
                        >
                            Send
                        </Button>
                    </div>
                </div>
            )}
        </div>
    );
};

export default Chat;
