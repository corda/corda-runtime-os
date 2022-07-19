import style from './pageContentWrapper.module.scss';

type Props = {
    children?: React.ReactNode;
    footerEnabled?: boolean;
};

const PageContentWrapper: React.FC<Props> = ({ children }) => {
    return (
        <>
            <div className={style.pageContentWrapper}>{children}</div>
        </>
    );
};

export default PageContentWrapper;
