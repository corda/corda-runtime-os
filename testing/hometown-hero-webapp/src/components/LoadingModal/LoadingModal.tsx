import { Loader } from '@r3/r3-tooling-design-system/exports';

const LoadingModal = () => {
    return (
        <div className="h-screen fixed w-full flex" style={{ backdropFilter: 'blur(10px)', zIndex: 1000 }}>
            <div className="m-auto">
                <Loader size="medium" />
            </div>
        </div>
    );
};

export default LoadingModal;
