import { IconButton } from '@r3/r3-tooling-design-system/exports';
import useAppDataContext from '@/contexts/AppDataContext';

const CpiList = () => {
    const { cpiList, refreshCpiList } = useAppDataContext();
    return (
        <div className="ml-20 mt-12" style={{ maxHeight: 600, overflowY: 'auto' }}>
            <div className="flex">
                <h2>CPI List</h2>{' '}
                <IconButton
                    className="ml-6"
                    icon={'Refresh'}
                    size={'large'}
                    variant={'secondary'}
                    onClick={refreshCpiList}
                />
            </div>
            {cpiList.length === 0 && <h3 className="mt-6 ml-4 opacity-75">No CPIs Uploaded</h3>}
            <div style={{ maxHeight: 500 }}>
                {cpiList.map((cpi, index) => {
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
                            {index === 0 && (
                                <p style={{ color: 'green!', fontSize: 18 }}>
                                    <strong>Will be used for all new Nodes </strong>{' '}
                                </p>
                            )}

                            <p>
                                <strong>Name: </strong> {cpi.id.cpiName}
                            </p>

                            <p>
                                <strong>Version:</strong> {cpi.id.cpiVersion}
                            </p>
                            <p>
                                <strong>CPKS:</strong>
                            </p>
                            {cpi.cpks.map((cpk) => (
                                <div key={cpk.id.name} style={{ border: '1px grey', padding: 8, borderRadius: 8 }}>
                                    <p>
                                        <strong>Name:</strong> {cpk.id.name}
                                    </p>
                                    <p>
                                        <strong> Main Bundle:</strong> {cpk.mainBundle}
                                    </p>
                                </div>
                            ))}
                        </div>
                    );
                })}
            </div>
            <div className="flex gap-4 mt-8 ml-4 flex-wrap"></div>
        </div>
    );
};

export default CpiList;
