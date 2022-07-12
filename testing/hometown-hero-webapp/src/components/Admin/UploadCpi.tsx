import { Button, FileUpload, NotificationService } from '@r3/r3-tooling-design-system/exports';

import adminAxiosInstance from '@/api/adminAxios';
import apiCall from '@/api/apiCall';
import { useState } from 'react';

const UploadCpi = () => {
    const [file, setFile] = useState<File | undefined>(undefined);

    const handleFileChange = (e: any) => {
        if (e.target.files && e.target.files.length > 0) {
            setFile(e.target.files[0]);
        }
    };

    const uploadCpi = async () => {
        if (!file) return;
        var formData = new FormData();
        //formData.append('cpiFileName', file.name);
        formData.append('upload', file);
        const response = await apiCall({
            method: 'post',
            path: '/api/v1/cpi',
            params: formData,
            axiosInstance: adminAxiosInstance,
        });

        if (response.error) {
            NotificationService.notify(`Failed to upload cpi: Error: ${response.error}`, 'Error', 'danger');
        } else {
            NotificationService.notify(`Successfully uploaded cpi!`, 'Success!', 'success');
        }
    };
    return (
        <div className="ml-20 mt-12">
            <h2>CPI Upload</h2>
            <div className="flex gap-4 mt-8 ml-4 flex-wrap">
                <div style={{ width: 350 }}>
                    <FileUpload
                        label={'Cpi Upload'}
                        filename={!file ? 'Click me to select a local CPI' : file.name}
                        onChange={handleFileChange}
                        onDelete={function (any: any) {
                            setFile(undefined);
                        }}
                    />
                </div>
                <Button iconLeft={'File'} size={'large'} variant={'primary'} disabled={!file} onClick={uploadCpi}>
                    Upload CPI
                </Button>
            </div>
        </div>
    );
};

export default UploadCpi;
