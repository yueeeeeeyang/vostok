import VkTable from './vk-table/VkTable.vue';
import VkForm from './vk-form/VkForm.vue';
import VkSearchBar from './vk-search-bar/VkSearchBar.vue';
import VkUpload from './vk-upload/VkUpload.vue';
import VkSelector from './vk-selector/VkSelector.vue';
import VkModalForm from './vk-modal-form/VkModalForm.vue';
import VkDrawerForm from './vk-drawer-form/VkDrawerForm.vue';
import VkAdminLayout from './vk-admin-layout/VkAdminLayout.vue';

export { VkTable, VkForm, VkSearchBar, VkUpload, VkSelector, VkModalForm, VkDrawerForm, VkAdminLayout };
export * from './vk-table/vk-table.types';
export * from './vk-form/vk-form.types';
export * from './vk-search-bar/vk-search-bar.types';
export * from './vk-upload/vk-upload.types';
export * from './vk-selector/vk-selector.types';
export * from './vk-admin-layout/vk-admin-layout.types';

export const componentRegistry = {
  VkTable,
  VkForm,
  VkSearchBar,
  VkUpload,
  VkSelector,
  VkModalForm,
  VkDrawerForm,
  VkAdminLayout
};
