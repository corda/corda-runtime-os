import { IconButton } from '@r3/r3-tooling-design-system/exports';
import useAppDataContext from '@/contexts/appDataContext';

const VNodesList = () => {
    const { vNodes, refreshVNodes } = useAppDataContext();
    return (
        <div className="ml-20 mt-12">
            <div className="flex mb-6">
                <h2>V-Nodes List</h2>{' '}
                <IconButton
                    className="ml-6"
                    icon={'Refresh'}
                    size={'large'}
                    variant={'secondary'}
                    onClick={refreshVNodes}
                />
            </div>
            {vNodes.length === 0 && <h3 className="mt-6 ml-4 opacity-75">No V-Nodes Created</h3>}
            <div style={{ maxHeight: 500, maxWidth: 500, overflowY: 'scroll' }}>
                {vNodes.map((vNode) => {
                    return (
                        <div
                            style={{
                                marginTop: 8,
                                border: '1px solid lightgrey',
                                padding: 12,
                                maxWidth: 400,
                                borderRadius: 12,
                            }}
                        >
                            <div>
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
                })}
            </div>
        </div>
    );
};

export default VNodesList;
