import { createRouter, createWebHistory } from 'vue-router';
import VkTableDemoPage from '../pages/components/VkTableDemoPage.vue';
import VkFormDemoPage from '../pages/components/VkFormDemoPage.vue';
import VkSearchBarDemoPage from '../pages/components/VkSearchBarDemoPage.vue';
import VkUploadDemoPage from '../pages/components/VkUploadDemoPage.vue';
import VkSelectorDemoPage from '../pages/components/VkSelectorDemoPage.vue';
import VkLoginDemoPage from '../pages/components/VkLoginDemoPage.vue';
import VkModalFormDemoPage from '../pages/components/VkModalFormDemoPage.vue';
import VkDrawerFormDemoPage from '../pages/components/VkDrawerFormDemoPage.vue';
import VkAdminLayoutDemoPage from '../pages/components/VkAdminLayoutDemoPage.vue';
import MessageCenterPage from '../pages/MessageCenterPage.vue';
import WorkbenchPage from '../pages/WorkbenchPage.vue';

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/components/vk-table'
    },
    {
      path: '/components/vk-table',
      name: 'component-vk-table',
      component: VkTableDemoPage
    },
    {
      path: '/components/vk-form',
      name: 'component-vk-form',
      component: VkFormDemoPage
    },
    {
      path: '/components/vk-search-bar',
      name: 'component-vk-search-bar',
      component: VkSearchBarDemoPage
    },
    {
      path: '/components/vk-upload',
      name: 'component-vk-upload',
      component: VkUploadDemoPage
    },
    {
      path: '/components/vk-selector',
      name: 'component-vk-selector',
      component: VkSelectorDemoPage
    },
    {
      path: '/components/vk-login',
      name: 'component-vk-login',
      component: VkLoginDemoPage
    },
    {
      path: '/components/vk-modal-form',
      name: 'component-vk-modal-form',
      component: VkModalFormDemoPage
    },
    {
      path: '/components/vk-drawer-form',
      name: 'component-vk-drawer-form',
      component: VkDrawerFormDemoPage
    },
    {
      path: '/components/vk-admin-layout',
      name: 'component-vk-admin-layout',
      component: VkAdminLayoutDemoPage
    },
    {
      path: '/message-center',
      name: 'message-center',
      component: MessageCenterPage
    },
    {
      path: '/pages/workbench',
      name: 'workbench',
      component: WorkbenchPage
    }
  ]
});
