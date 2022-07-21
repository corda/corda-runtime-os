import { useCallback, useEffect, useState } from 'react';

import { ADMIN_AUTH_CONFIG } from '@/constants/authAdmin';
import { Cpi } from '@/models/cpi';
import { VirtualNode } from '@/models/virtualnode';
import apiCall from '@/api/apiCall';
import createCtx from './createCtx';

type AppDataContextProps = {
    cpiList: Cpi[];
    vNodes: VirtualNode[];
    refreshCpiList: () => void;
    refreshVNodes: () => Promise<VirtualNode[]>;
};

const [useAppDataContext, Provider] = createCtx<AppDataContextProps>();
export default useAppDataContext;

type Props = {
    children?: React.ReactNode;
};

export const AppDataContextProvider: React.FC<Props> = ({ children }) => {
    const [cpiList, setCpiList] = useState<Cpi[]>([]);
    const [vNodes, setVNodes] = useState<VirtualNode[]>([]);

    const refreshCpiList = useCallback(async () => {
        const response = await apiCall({
            method: 'get',
            path: '/api/v1/cpi',
            auth: ADMIN_AUTH_CONFIG,
        });
        setCpiList(response.data.cpis);
    }, []);

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
        refreshCpiList();
        refreshVNodes();

        const interval = setInterval(() => {
            refreshVNodes();
        }, 2000);

        return () => {
            clearInterval(interval);
        };
    }, []);

    return <Provider value={{ cpiList, vNodes, refreshCpiList, refreshVNodes }}>{children}</Provider>;
};
