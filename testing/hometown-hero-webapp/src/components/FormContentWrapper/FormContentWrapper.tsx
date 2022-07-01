import style from './formContentWrapper.module.scss';

type Props = {
    children?: React.ReactNode;
};

const FormContentWrapper: React.FC<Props> = ({ children }) => {
    return <div className={style.formContentWrapper}>{children}</div>;
};

export default FormContentWrapper;
