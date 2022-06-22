import { createContext, useContext } from 'react';

const createCtx = <T extends {} | null>() => {
    const ctx = createContext<T | undefined>(undefined);
    function useCtx() {
        const uC = useContext(ctx);
        if (uC === undefined)
            throw new Error(
                `useContext must be called from within a Provider with a value. There may be no provider specified as a parent of the consumer.`
            );
        return uC;
    }
    return [useCtx, ctx.Provider] as const;
};

export default createCtx;
