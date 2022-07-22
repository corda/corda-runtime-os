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

const DEFAULT_COLOR = '#000';

export const LOCATION_COLORS: Map<string, string> = new Map([
    ['IE', '#84DE6C'],
    ['GB', '#839FF0'],
    ['US', '#F65D5D'],
    ['SG', '#f57171'],
    ['IN', '#fc7f03'],
    ['default', DEFAULT_COLOR],
]);

export const LOCATION_GROUP_COORDS = new Map([
    ['IE', { x: 800, y: 0 }],
    ['GB', { x: 0, y: 800 }],
    ['US', { x: -800, y: -800 }],
    ['SG', { x: -800, y: 0 }],
    ['IN', { x: 800, y: -800 }],
    ['default', { x: 0, y: 0 }],
]);
