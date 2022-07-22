import { GRAPH_OPTIONS, LOCATION_COLORS, LOCATION_GROUP_COORDS } from './options';
import { useCallback, useEffect, useState } from 'react';

import { Button } from '@r3/r3-tooling-design-system/exports';
import Graph from 'react-graph-vis';
import style from './networkVisualizer.module.scss';
import useAppDataContext from '@/contexts/appDataContext';
import useGetMessageLogs from '@/hooks/useGetMessageLogs';

const GRAPH_INITIAL_STATE = {
    counter: 0,
    nodes: [],
    edges: [],
};

const REMOVE_MESSAGE_INTERVAL_MS = 2000;
const MESSAGE_MAX_AGE = 5000;

const NetworkVisualizer = () => {
    const { vNodes } = useAppDataContext();
    const { messageLogs, fetchMessageLogs } = useGetMessageLogs();

    const [network, setNetwork] = useState<any | undefined>(undefined);
    const [graphData, setGraphData] = useState<any>(GRAPH_INITIAL_STATE);
    const [isEnlarged, setIsEnlarged] = useState<boolean>(false);

    const cleanUpOldMessages = useCallback(() => {
        const filterOldMessages = (graphData: any) => {
            const newGraphData = { ...graphData };
            const now = Date.now();
            newGraphData.edges = newGraphData.edges.filter((edge: any) => {
                const difference = now - new Date(edge.timestamp).getTime();
                const secondsDifference = Math.floor(difference);
                if (secondsDifference >= MESSAGE_MAX_AGE) {
                    return false;
                }
                return true;
            });
            return newGraphData;
        };
        setGraphData((newGraphData: any) => filterOldMessages(newGraphData));
    }, []);

    useEffect(() => {
        fetchMessageLogs();

        const interval = setInterval(() => {
            cleanUpOldMessages();
        }, REMOVE_MESSAGE_INTERVAL_MS);

        const fetchInterval = setInterval(() => {
            fetchMessageLogs();
        }, 2000);

        return () => {
            clearInterval(interval);
            clearInterval(fetchInterval);
        };
    }, [cleanUpOldMessages]);

    const addMessage = useCallback(
        (fromId: number, toId: number) => {
            const newGraphData = { ...graphData };
            newGraphData.edges = [
                ...newGraphData.edges,
                { from: fromId, to: toId, arrows: 'to', dashes: true, timestamp: Date.now() },
            ];
            newGraphData.counter = newGraphData.counter + 1;
            setGraphData(newGraphData);
        },
        [graphData]
    );

    useEffect(() => {
        if (messageLogs.length === 0) return;
        const newGraphData = { ...graphData };

        messageLogs.forEach((messageLog) => {
            const now = Date.now();
            const difference = now - new Date(messageLog.timestamp).getTime();
            const secondsDifference = Math.floor(difference);
            if (secondsDifference >= MESSAGE_MAX_AGE) {
                return;
            }
            if (
                newGraphData.edges.findIndex(
                    (edge: any) =>
                        edge.from === messageLog.sender &&
                        edge.to === messageLog.receiver &&
                        edge.timestamp === messageLog.timestamp
                ) > -1
            ) {
                return;
            }
            newGraphData.edges = [
                ...newGraphData.edges,
                {
                    from: messageLog.sender,
                    to: messageLog.receiver,
                    arrows: 'to',
                    dashes: true,
                    timestamp: Date.now(),
                },
            ];
        });

        newGraphData.counter = newGraphData.counter + 1;
        setGraphData(newGraphData);
    }, [messageLogs]);

    useEffect(() => {
        const newNodes: any[] = [];
        vNodes.forEach(({ holdingIdentity }) => {
            const x500Name = holdingIdentity.x500Name;
            let location = 'default';
            if (x500Name.includes('C=IE')) {
                location = 'IE';
            } else if (x500Name.includes('C=GB')) {
                location = 'GB';
            } else if (x500Name.includes('C=US')) {
                location = 'US';
            } else if (x500Name.includes('C=IN')) {
                location = 'IN';
            } else if (x500Name.includes('C=SG')) {
                location = 'SG';
            }
            const locCoords = LOCATION_GROUP_COORDS.get(location);
            const newNode = {
                id: x500Name,
                label: x500Name.split('node,')[0],
                title: `${name} tooltip text`,
                x: locCoords?.x ?? undefined,
                y: locCoords?.y ?? undefined,
                color: LOCATION_COLORS.get(location),
                location: location,
            };
            newNodes.push(newNode);
        });
        const tempGraphData = { ...graphData };
        tempGraphData.counter = tempGraphData.counter + 1;
        tempGraphData.nodes = newNodes;
        setGraphData(tempGraphData);
    }, [vNodes]);

    const groupNodes = useCallback(() => {
        if (!network) return;
        graphData.nodes.forEach((node: any) => {
            const locCoords = LOCATION_GROUP_COORDS.get(node.location);
            if (locCoords) {
                network.moveNode(node.id, locCoords.x, locCoords.y);
            }
        });
    }, [graphData, network]);

    return (
        <div>
            <div className="flex gap-6 mt-6 mb-6">
                <div className="flex gap-6 ml-4">
                    <Button
                        size={'small'}
                        variant={'primary'}
                        onClick={() => {
                            setIsEnlarged((prev) => !prev);
                        }}
                    >
                        {isEnlarged ? 'Smaller' : 'Bigger'}
                    </Button>
                    <Button size={'small'} variant={'primary'} onClick={groupNodes}>
                        Group Nodes
                    </Button>
                </div>
            </div>
            <div className={`${style.networkVizWrapper} ${isEnlarged ? style.enlarged : ''} shadow-xl`}>
                <Graph
                    graph={{ nodes: graphData.nodes, edges: graphData.edges }}
                    options={GRAPH_OPTIONS}
                    getNetwork={(network: any) => {
                        setNetwork(network);
                    }}
                />
            </div>
        </div>
    );
};

export default NetworkVisualizer;
