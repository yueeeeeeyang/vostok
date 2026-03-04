<template>
  <VkAdminLayout
    title="Vostok Frontend"
    menu-api-path="/menus"
    menu-api-app-key="appKey"
    :menu-field-map="menuFieldMap"
    app-api-path="/apps"
    :app-field-map="appFieldMap"
    :recommend-app-count="2"
    :default-expanded-keys="['pages', 'components']"
    :language-options="languageOptions"
    current-language="zh-CN"
    current-theme="light"
    :user="currentUser"
    :user-menus="userMenus"
    :recent-notifications="recentNotifications"
    :notification-count="recentNotifications.length"
    message-center-path="/message-center"
    message-center-button-text="进入消息中心"
  />
</template>

<script setup lang="ts">
import { VkAdminLayout } from '@vostok/frontend';
import type {
  VkAppFieldMap,
  VkLanguageOption,
  VkMenuFieldMap,
  VkNotificationItem,
  VkUserProfile,
  VkUserMenuItem
} from '@vostok/frontend';

const menuFieldMap: VkMenuFieldMap = {
  key: 'menuId',
  label: 'menuName',
  path: 'routePath',
  children: 'subMenus'
};

const appFieldMap: VkAppFieldMap = {
  key: 'appId',
  label: 'appName',
  icon: 'iconName',
  path: 'routePath',
  recommended: 'isRecommended'
};

const languageOptions: VkLanguageOption[] = [
  { value: 'zh-CN', label: '简体中文' },
  { value: 'en-US', label: 'English' }
];

const userMenus: VkUserMenuItem[] = [
  { key: 'profile', label: '个人中心' },
  { key: 'password', label: '修改密码' },
  { key: 'logout', label: '退出登录' }
];

const currentUser: VkUserProfile = {
  id: 'u-admin',
  avatar: '',
  name: '管理员',
  username: 'admin'
};

const recentNotifications: VkNotificationItem[] = [
  {
    key: 'msg-1',
    title: '系统升级通知',
    summary: '本周六 22:00 开始维护，预计 30 分钟。',
    time: '刚刚',
    path: '/message-center'
  },
  {
    key: 'msg-2',
    title: '审批提醒',
    summary: '你有 3 条待审批任务，请及时处理。',
    time: '5 分钟前',
    path: '/message-center'
  },
  {
    key: 'msg-3',
    title: '数据导出完成',
    summary: '用户列表导出已完成，可在下载中心查看。',
    time: '20 分钟前',
    path: '/message-center'
  },
  {
    key: 'msg-4',
    title: '安全告警',
    summary: '检测到异地登录，请确认账号安全。',
    time: '1 小时前',
    path: '/message-center'
  },
  {
    key: 'msg-5',
    title: '版本发布成功',
    summary: 'v1.9.1.0 已部署到预发环境。',
    time: '2 小时前',
    path: '/message-center'
  }
];
</script>
