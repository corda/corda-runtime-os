import { useCallback, useEffect, useState } from 'react';

import { ADMIN_AUTH_CONFIG } from '@/constants/authAdmin';
import { VirtualNode } from '@/models/virtualnode';
import apiCall from '@/api/apiCall';

const useGetVNodes = (withAutoRefresh?: boolean) => {
    const [vNodes, setVNodes] = useState<VirtualNode[]>([]);

    const refreshVNodes = useCallback(async () => {
        const response = await apiCall({
            method: 'get',
            path: '/api/v1/virtualnode',
            dontTrackRequest: true,
            auth: ADMIN_AUTH_CONFIG,
        });
        setVNodes(response.data.virtualNodes);
        return response.data.virtualNodes;
    }, []);

    useEffect(() => {
        refreshVNodes();

        if (!withAutoRefresh) {
            return;
        }

        const interval = setInterval(() => {
            refreshVNodes();
        }, 2000);

        return () => {
            clearInterval(interval);
        };
    }, [withAutoRefresh]);

    return { vNodes, refreshVNodes };
};

export default useGetVNodes;
