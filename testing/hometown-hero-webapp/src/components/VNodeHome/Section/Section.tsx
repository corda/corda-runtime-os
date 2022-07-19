import React from 'react';
import style from './section.module.scss';

const Section: React.FC<{ title?: string; children?: React.ReactNode }> = ({ children, title }) => {
    return (
        <div className={`${style.section} shadow-md divide-y divide-black`}>
            {title && (
                <>
                    <h2 className={style.title}>{title}</h2>
                </>
            )}
            {children}
        </div>
    );
};

export default Section;
