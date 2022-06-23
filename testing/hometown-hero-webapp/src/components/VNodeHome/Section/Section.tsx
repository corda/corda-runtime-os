import React from 'react';

const Section: React.FC<{ title?: string; children?: React.ReactNode }> = ({ children, title }) => {
    return (
        <div
            className="section bg-light-gray-400 rounded-lg shadow-md p-6 divide-y divide-black divide-opacity-20 "
            style={{ height: 'fit-content' }}
        >
            {title && (
                <>
                    <h2 className="opacity-75 text-2xl sm:text-4xl md:text-4xl lg:text-4xl">{title}</h2>
                    <br />
                </>
            )}
            {children}
        </div>
    );
};

export default Section;
