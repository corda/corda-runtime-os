import { Options } from 'vis';

export const GRAPH_OPTIONS: Options = {
    autoResize: true,
    layout: { improvedLayout: true, randomSeed: 2 },
    edges: {
        smooth: true,
        color: '#000000',
        physics: true,
        scaling: {
            min: 1,
            max: 100,
        },
        width: 2,
    },
    nodes: {
        shape: 'dot',
        scaling: {
            label: {
                min: 8,
                max: 20,
            },
        },
        shadow: true,
    },
    physics: {
        forceAtlas2Based: {
            gravitationalConstant: -25,
            centralGravity: 0.002,
            springLength: 400,
            springConstant: 0.002,
        },
        maxVelocity: 146,
        solver: 'forceAtlas2Based',
        timestep: 0.35,
        stabilization: { iterations: 150 },
    },
};

export const LOCATIONS: string[] = ['Ireland', 'UK', 'USA'];

export const LOCATION_COLORS: Map<string, string> = new Map([
    ['Ireland', '#84DE6C'],
    ['UK', '#839FF0'],
    ['USA', '#F65D5D'],
]);

export const LOCATION_GROUP_COORDS = new Map([
    ['Ireland', { x: 800, y: 0 }],
    ['UK', { x: 0, y: 800 }],
    ['USA', { x: 800, y: -800 }],
]);
