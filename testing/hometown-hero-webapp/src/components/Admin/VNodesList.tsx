import { IconButton } from '@r3/r3-tooling-design-system/exports';
import useAppDataContext from '@/contexts/AppDataContext';

const VNodesList = () => {
    const { vNodes, refreshVNodes } = useAppDataContext();
    return (
        <div className="ml-20 mt-12">
            <div className="flex">
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
        </div>
    );
};

export default VNodesList;
