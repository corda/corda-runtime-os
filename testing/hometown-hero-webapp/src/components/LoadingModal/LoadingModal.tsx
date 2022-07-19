import { Loader } from '@r3/r3-tooling-design-system/exports';
import style from './loadingModal.module.scss';

const LoadingModal = () => {
    return (
        <div className={style.loadingModal}>
            <div className="m-auto">
                <Loader size="medium" />
            </div>
        </div>
    );
};

export default LoadingModal;
