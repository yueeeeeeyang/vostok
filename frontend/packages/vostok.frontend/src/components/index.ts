import ProTable from './pro-table/ProTable.vue';
import ProForm from './pro-form/ProForm.vue';
import ProSearchBar from './pro-search/ProSearchBar.vue';
import ProUpload from './pro-upload/ProUpload.vue';
import ProModalForm from './pro-modal-form/ProModalForm.vue';
import ProDrawerForm from './pro-drawer-form/ProDrawerForm.vue';

export { ProTable, ProForm, ProSearchBar, ProUpload, ProModalForm, ProDrawerForm };
export * from './pro-table/pro-table.types';
export * from './pro-form/pro-form.types';
export * from './pro-search/pro-search.types';
export * from './pro-upload/upload.types';

export const componentRegistry = {
  ProTable,
  ProForm,
  ProSearchBar,
  ProUpload,
  ProModalForm,
  ProDrawerForm
};
