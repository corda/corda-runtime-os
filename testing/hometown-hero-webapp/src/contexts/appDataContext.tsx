import { useEffect, useState } from 'react';

import { Cpi } from '@/models/cpi';
import { VirtualNode } from '@/models/virtualnode';
import adminAxiosInstance from '@/api/adminAxios';
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

    const refreshCpiList = async () => {
        const response = await apiCall({ method: 'get', path: '/api/v1/cpi', axiosInstance: adminAxiosInstance });
        setCpiList(response.data.cpis);
    };
    const refreshVNodes = async () => {
        const response = await apiCall({
            method: 'get',
            path: '/api/v1/virtualnode',
            axiosInstance: adminAxiosInstance,
            dontTrackRequest: true,
        });
        setVNodes(response.data.virtualNodes);
        return response.data.virtualNodes;
    };

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
