import { GRAPH_OPTIONS, LOCATIONS, LOCATION_COLORS, LOCATION_GROUP_COORDS } from './options';
import { useCallback, useEffect, useState } from 'react';

import { Button } from '@r3/r3-tooling-design-system/exports';
import Graph from 'react-graph-vis';
import style from './networkVisualizer.module.scss';

function getRandomInt(max: number) {
    return Math.floor(Math.random() * max);
}

const GRAPH_INITIAL_STATE = {
    counter: 0,
    nodes: [],
    edges: [],
};

//https://ipapi.co/json/

const REMOVE_MESSAGE_INTERVAL_MS = 2000;

const NetworkVisualizer = () => {
    const [network, setNetwork] = useState<any | undefined>(undefined);
    const [graphData, setGraphData] = useState<any>(GRAPH_INITIAL_STATE);

    const cleanUpOldMessages = useCallback(() => {
        const filterOldMessages = (graphData: any) => {
            const newGraphData = { ...graphData };
            const now = Date.now();
            newGraphData.edges = newGraphData.edges.filter((edge: any) => {
                const difference = now - new Date(edge.timestamp).getTime();
                const secondsDifference = Math.floor(difference);
                if (secondsDifference >= REMOVE_MESSAGE_INTERVAL_MS) {
                    return false;
                }
                return true;
            });
            return newGraphData;
        };
        setGraphData((newGraphData: any) => filterOldMessages(newGraphData));
    }, []);

    useEffect(() => {
        const interval = setInterval(() => {
            cleanUpOldMessages();
        }, REMOVE_MESSAGE_INTERVAL_MS);
        return () => {
            clearInterval(interval);
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

    const addNode = useCallback(
        (name: string, location: string) => {
            const locCoords = LOCATION_GROUP_COORDS.get(location);
            const newNode = {
                id: graphData.nodes.length + 2,
                label: name + ' ' + (graphData.nodes.length + 1),
                title: `${name} tooltip text`,
                x: locCoords?.x ?? undefined,
                y: locCoords?.y ?? undefined,
                color: LOCATION_COLORS.get(location),
                location: location,
            };
            const tempGraphData = { ...graphData };
            tempGraphData.counter = tempGraphData.counter + 1;
            tempGraphData.nodes = [...tempGraphData.nodes, newNode];
            setGraphData(tempGraphData);
        },
        [graphData]
    );

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
            {/* Temp buttons to show functionality */}
            <div className="flex gap-6 mt-6 mb-6">
                <Button
                    size={'small'}
                    variant={'primary'}
                    onClick={() => {
                        addMessage(getRandomInt(graphData.nodes.length - 1), getRandomInt(graphData.nodes.length - 1));
                    }}
                >
                    Add Message
                </Button>
                <Button
                    size={'small'}
                    variant={'primary'}
                    onClick={() => {
                        addNode(
                            `O=${'Node' + (graphData.nodes.length + 1)}`,
                            LOCATIONS[getRandomInt(LOCATIONS.length)]
                        );
                    }}
                >
                    Add New Node
                </Button>
                <Button size={'small'} variant={'primary'} onClick={groupNodes}>
                    Group Nodes
                </Button>
            </div>
            <div className={`${style.networkVizWrapper} shadow-xl`}>
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
