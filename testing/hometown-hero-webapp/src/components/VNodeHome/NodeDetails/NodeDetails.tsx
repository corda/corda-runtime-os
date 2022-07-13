import style from './nodeDetails.module.scss';
import useAppDataContext from '@/contexts/appDataContext';
import { useEffect } from 'react';
import useUserContext from '@/contexts/userContext';

const NodeDetails = () => {
    const { vNodes } = useAppDataContext();
    const { vNode, setVNode, holderShortId } = useUserContext();

    useEffect(() => {
        if (vNode) return;
        const myVNode = vNodes.find((vNode) => vNode.holdingIdentity.id === holderShortId);
        setVNode(myVNode);
    }, [vNode]);

    if (!vNode) {
        return <></>;
    }

    return (
        <div className={style.nodeDetails}>
            <div
                style={{
                    marginTop: 8,
                    borderRadius: 12,
                }}
            >
                <p>
                    <strong>x500 Name:</strong> {vNode.holdingIdentity.x500Name}
                </p>
                <p>
                    <strong>Group ID:</strong> {vNode.holdingIdentity.groupId}
                </p>
                <p>
                    <strong>Holding ID:</strong> {vNode.holdingIdentity.id}
                </p>
                <p>
                    <strong>Cpi : </strong>
                    {vNode.cpiIdentifier.name}
                </p>
            </div>
        </div>
    );
};

export default NodeDetails;
