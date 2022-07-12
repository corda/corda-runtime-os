import CpiList from '@/components/Admin/CpiList';
import PageContentWrapper from '@/components/PageContentWrapper/PageContentWrapper';
import PageHeader from '@/components/PageHeader/PageHeader';
import UploadCpi from '@/components/Admin/UploadCpi';
import VNodesList from '@/components/Admin/VNodesList';

const Admin = () => {
    return (
        <PageContentWrapper>
            <div className="mt-24 sm:mt-0 md:mt-0 lg:mt-0 flex" />
            <PageHeader withBackButton>Admin Stuff</PageHeader>
            <div className="flex">
                <div className="flex flex-col gap-12">
                    <UploadCpi />
                    <CpiList />
                </div>
                <VNodesList />
            </div>
        </PageContentWrapper>
    );
};

export default Admin;
