import { useCallback, useState } from 'react';

import apiCall from '@/api/apiCall';

type MessageLog = {
    sender: string;
    receiver: string;
    timestamp: number;
};

const useGetMessageLogs = () => {
    const [messageLogs, setMessageLogs] = useState<MessageLog[]>([]);

    const fetchMessageLogs = useCallback(async () => {
        console.log(Date.now());

        const response = await apiCall({
            method: 'get',
            baseUrl: 'http://app.hth-demo.corda.cloud',
            path: '/messages',
            dontTrackRequest: true,
        });

        if (response.error) {
            console.log(response.error);
            return;
        }
        setMessageLogs(response.data);
    }, []);

    return { messageLogs, fetchMessageLogs };
};

export default useGetMessageLogs;
