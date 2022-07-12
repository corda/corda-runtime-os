export interface Dependency {
    name: string;
    signerSummaryHash: string;
    version: string;
}

export interface Id {
    name: string;
    signerSummaryHash: string;
    version: string;
}

export interface Cpk {
    dependencies: Dependency[];
    hash: string;
    id: Id;
    libraries: string[];
    mainBundle: string;
    type: string;
}

export interface Id2 {
    cpiName: string;
    cpiVersion: string;
    signerSummaryHash: string;
}

export interface Cpi {
    cpks: Cpk[];
    fileChecksum: string;
    groupPolicy: string;
    id: Id2;
}

export interface Cpis {
    cpis: Cpi[];
}
