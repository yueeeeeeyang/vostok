<template>
  <n-config-provider :theme="isDarkTheme ? darkTheme : undefined">
    <n-layout
      :class="['vf-vk-admin-layout', { 'vf-vk-admin-layout--dark': isDarkTheme }]"
      :style="layoutStyle"
    >
    <n-layout-header v-if="showHeader" bordered class="vf-vk-admin-layout__header">
      <div class="vf-vk-admin-layout__header-brand" :style="headerTitleZoneStyle">
        <div class="vf-vk-admin-layout__header-brand-inner">
          <h1 class="vf-vk-admin-layout__title">{{ title }}</h1>
        </div>
      </div>

      <div class="vf-vk-admin-layout__header-main">
        <n-button
          v-for="app in quickApps"
          :key="app.key"
          :class="[
            'vf-vk-admin-layout__quick-app-btn',
            { 'vf-vk-admin-layout__quick-app-btn--active': activeAppKey === app.key }
          ]"
          quaternary
          @click="handleQuickAppClick(app)"
        >
          <template #icon>
            <n-icon>
              <component :is="resolveHeaderAppIcon(app.icon)" />
            </n-icon>
          </template>
          {{ app.label }}
        </n-button>

        <n-popover trigger="click" placement="bottom-start" :content-style="{ padding: '0' }">
          <template #trigger>
            <n-button quaternary class="vf-vk-admin-layout__all-apps-btn">
              <template #icon>
                <n-icon>
                  <AppsOutline />
                </n-icon>
              </template>
              <span>全部应用</span>
              <n-icon size="14" class="vf-vk-admin-layout__all-apps-icon">
                <ChevronDownOutline />
              </n-icon>
            </n-button>
          </template>
          <div class="vf-vk-admin-layout__all-apps-panel">
            <div class="vf-vk-admin-layout__all-apps-title">全部应用</div>
            <n-empty v-if="allApps.length === 0" description="暂无应用" />
            <n-grid v-else :cols="4" :x-gap="8" :y-gap="8">
              <n-gi v-for="app in allApps" :key="app.key">
                <n-button
                  :class="[
                    'vf-vk-admin-layout__all-apps-tile',
                    { 'vf-vk-admin-layout__all-apps-tile--active': activeAppKey === app.key }
                  ]"
                  quaternary
                  @click="handleAllAppClick(app)"
                >
                  <n-icon class="vf-vk-admin-layout__all-apps-tile-icon">
                    <component :is="resolveHeaderAppIcon(app.icon)" />
                  </n-icon>
                  <span class="vf-vk-admin-layout__all-apps-tile-label">{{ app.label }}</span>
                </n-button>
              </n-gi>
            </n-grid>
          </div>
        </n-popover>

        <n-input
          class="vf-vk-admin-layout__global-search"
          clearable
          :value="globalKeyword"
          placeholder="全局搜索"
          @update:value="onGlobalKeywordChange"
          @keyup.enter="handleGlobalSearch"
        >
        </n-input>
      </div>

      <div class="vf-vk-admin-layout__header-actions">
        <n-popover trigger="click" placement="bottom-end" :content-style="{ padding: '0' }">
          <template #trigger>
            <n-badge :value="notificationCount" :max="99" :show-zero="false">
              <n-button quaternary circle @click="handleNotificationClick">
                <template #icon>
                  <n-icon>
                    <NotificationsOutline />
                  </n-icon>
                </template>
              </n-button>
            </n-badge>
          </template>
          <div class="vf-vk-admin-layout__notice-panel">
            <div class="vf-vk-admin-layout__notice-body">
              <div class="vf-vk-admin-layout__notice-title">最近消息</div>
              <div v-if="recentNotificationItems.length === 0" class="vf-vk-admin-layout__notice-empty">
                <n-empty description="暂无消息" />
              </div>
              <div v-else class="vf-vk-admin-layout__notice-list">
                <div
                  v-for="item in recentNotificationItems"
                  :key="item.key"
                  class="vf-vk-admin-layout__notice-item"
                  @click="handleNotificationItemClick(item)"
                >
                  <div class="vf-vk-admin-layout__notice-item-main">
                    <div class="vf-vk-admin-layout__notice-item-title">{{ item.title }}</div>
                    <div v-if="item.summary" class="vf-vk-admin-layout__notice-item-summary">
                      {{ item.summary }}
                    </div>
                  </div>
                  <div v-if="item.time" class="vf-vk-admin-layout__notice-item-time">{{ item.time }}</div>
                </div>
              </div>
            </div>
            <div class="vf-vk-admin-layout__notice-footer">
              <button type="button" class="vf-vk-admin-layout__notice-footer-btn" @click="handleMessageCenterClick">
                {{ messageCenterButtonText }}
              </button>
            </div>
          </div>
        </n-popover>

        <n-dropdown trigger="click" :options="languageDropdownOptions" @select="handleLanguageSelect">
          <n-button quaternary circle title="切换语言">
            <template #icon>
              <n-icon>
                <LanguageOutline />
              </n-icon>
            </template>
          </n-button>
        </n-dropdown>

        <n-button
          quaternary
          circle
          :title="isDarkTheme ? '切换浅色模式' : '切换深色模式'"
          @click="handleThemeToggle"
        >
          <template #icon>
            <n-icon>
              <component :is="isDarkTheme ? SunnyOutline : MoonOutline" />
            </n-icon>
          </template>
        </n-button>

        <n-dropdown :options="userMenuOptions" @select="handleUserMenuSelect">
          <n-button quaternary class="vf-vk-admin-layout__user-panel">
            <n-avatar round color="#dbeafe" text-color="#1d4ed8" :src="userAvatarUrl || undefined">
              {{ userAvatarText }}
            </n-avatar>
            <span class="vf-vk-admin-layout__user-meta">
              <span class="vf-vk-admin-layout__user-name">{{ userDisplayName }}</span>
              <span class="vf-vk-admin-layout__user-handle">{{ userHandle }}</span>
            </span>
            <n-icon size="14">
              <ChevronDownOutline />
            </n-icon>
          </n-button>
        </n-dropdown>
      </div>
    </n-layout-header>

    <n-layout has-sider class="vf-vk-admin-layout__body" :style="bodyStyle">
      <n-layout-sider
        bordered
        class="vf-vk-admin-layout__sider"
        collapse-mode="width"
        :collapsed-width="menuCollapsedWidth"
        :width="menuWidth"
        :style="siderStyle"
        :collapsed="innerCollapsed"
        :show-trigger="collapsible ? 'arrow-circle' : false"
        @update:collapsed="handleCollapsedChange"
      >
        <n-scrollbar class="vf-vk-admin-layout__menu-scroll">
          <div v-if="loading" class="vf-vk-admin-layout__state">
            <n-spin size="small" />
          </div>

          <div v-else-if="menuLoadError" class="vf-vk-admin-layout__state">
            <n-empty description="菜单加载失败">
              <template #extra>
                <n-button secondary @click="handleReloadMenu">
                  <template #icon>
                    <n-icon>
                      <RefreshOutline />
                    </n-icon>
                  </template>
                  重试
                </n-button>
              </template>
            </n-empty>
          </div>

          <div v-else-if="menuItems.length === 0" class="vf-vk-admin-layout__state">
            <n-empty description="暂无菜单数据" />
          </div>

          <n-menu
            v-else
            :options="menuOptions"
            :value="selectedKey ?? undefined"
            :expanded-keys="expandedKeys"
            :collapsed="innerCollapsed"
            :collapsed-width="menuCollapsedWidth"
            @update:value="handleMenuSelect"
            @update:expanded-keys="handleExpandedKeys"
          />
        </n-scrollbar>
      </n-layout-sider>

      <n-layout-content class="vf-vk-admin-layout__content">
        <slot>
          <RouterView v-if="hasRouter" />
          <n-empty v-else description="未检测到路由实例，请先安装并注册 vue-router。" />
        </slot>
      </n-layout-content>
    </n-layout>
    </n-layout>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, getCurrentInstance, h, inject, onMounted, ref, watch } from 'vue';
import {
  NAvatar,
  NBadge,
  NButton,
  NConfigProvider,
  NDropdown,
  NEmpty,
  NGi,
  NGrid,
  NIcon,
  NInput,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NPopover,
  NScrollbar,
  NSpin,
  darkTheme,
  type DropdownOption,
  type MenuOption
} from 'naive-ui';
import {
  AppsOutline,
  BookOutline,
  ChevronDownOutline,
  CloudOutline,
  CubeOutline,
  DocumentTextOutline,
  LanguageOutline,
  LayersOutline,
  MoonOutline,
  NotificationsOutline,
  RocketOutline,
  RefreshOutline,
  SearchOutline,
  SunnyOutline
} from '@vicons/ionicons5';
import { RouterView } from 'vue-router';
import type { RouteLocationNormalizedLoaded, Router } from 'vue-router';
import { API_CLIENT_KEY } from '../../symbols';
import type { ApiClient } from '../../types/public';
import { normalizeVkMenus } from './vk-menu-normalizer';
import type {
  VkAdminLayoutProps,
  VkAppFieldMap,
  VkHeaderAppItem,
  VkMenuItem,
  VkNotificationItem,
  VkUserProfile,
  VkThemeMode
} from './vk-admin-layout.types';

const props = withDefaults(defineProps<VkAdminLayoutProps>(), {
  showHeader: true,
  headerHeight: 64,
  menuWidth: 260,
  menuCollapsedWidth: 64,
  collapsed: false,
  collapsible: true,
  recommendAppCount: 2,
  menuApiAppKey: 'appKey',
  defaultExpandedKeys: () => [],
  menuApiQuery: () => ({}),
  appApiQuery: () => ({})
});

const emit = defineEmits<{
  (event: 'menu-click', item: VkMenuItem): void;
  (event: 'menu-loaded', items: VkMenuItem[]): void;
  (event: 'menu-load-error', error: unknown): void;
  (event: 'update:collapsed', value: boolean): void;
  (event: 'quick-app-click', app: VkHeaderAppItem): void;
  (event: 'all-app-click', app: VkHeaderAppItem): void;
  (event: 'global-search', keyword: string): void;
  (event: 'notification-click'): void;
  (event: 'notification-item-click', item: VkNotificationItem): void;
  (event: 'message-center-click'): void;
  (event: 'language-change', value: string): void;
  (event: 'theme-change', value: VkThemeMode): void;
  (event: 'user-menu-click', key: string): void;
}>();

const apiClient = inject<ApiClient | null>(API_CLIENT_KEY, null);
const currentInstance = getCurrentInstance();

const loading = ref(false);
const menuItems = ref<VkMenuItem[]>([]);
const appItems = ref<VkHeaderAppItem[]>([]);
const activeAppKey = ref<string>('');
const menuLoadError = ref<string | null>(null);
const selectedKey = ref<string | null>(props.defaultSelectedKey ?? null);
const expandedKeys = ref<string[]>([...(props.defaultExpandedKeys ?? [])]);
const innerCollapsed = ref(props.collapsed ?? false);
const globalKeyword = ref('');
const currentLanguageValue = ref(props.currentLanguage ?? '');
const currentThemeValue = ref<VkThemeMode>(props.currentTheme ?? 'light');

const headerHeight = computed(() => Math.max(props.headerHeight ?? 64, 0));
const menuWidth = computed(() => props.menuWidth ?? 260);
const menuCollapsedWidth = computed(() => props.menuCollapsedWidth ?? 64);
const showHeader = computed(() => props.showHeader ?? true);
const collapsible = computed(() => props.collapsible ?? true);
const recommendAppCount = computed(() => Math.max(props.recommendAppCount ?? 0, 0));
const allApps = computed<VkHeaderAppItem[]>(() => {
  if (appItems.value.length > 0) {
    return appItems.value;
  }
  return props.allApps ?? [];
});
const quickApps = computed<VkHeaderAppItem[]>(() => {
  // 推荐应用优先使用后端 recommended 标记，不足时按顺序补齐到指定数量。
  if (appItems.value.length === 0 && (props.quickApps ?? []).length > 0) {
    return (props.quickApps ?? []).slice(0, recommendAppCount.value);
  }
  const recommendCount = recommendAppCount.value;
  if (recommendCount === 0) {
    return [];
  }
  const recommended = allApps.value.filter((item) => item.recommended).slice(0, recommendCount);
  if (recommended.length >= recommendCount) {
    return recommended;
  }
  const pickedKeys = new Set(recommended.map((item) => item.key));
  for (const item of allApps.value) {
    if (pickedKeys.has(item.key)) {
      continue;
    }
    recommended.push(item);
    if (recommended.length >= recommendCount) {
      break;
    }
  }
  return recommended;
});
const activeApp = computed<VkHeaderAppItem | null>(
  () => allApps.value.find((item) => item.key === activeAppKey.value) ?? null
);
const notificationCount = computed(() => props.notificationCount ?? 0);
const recentNotificationItems = computed(() => (props.recentNotifications ?? []).slice(0, 5));
const messageCenterButtonText = computed(() => props.messageCenterButtonText ?? '');
const userProfile = computed<VkUserProfile | null>(() => props.user ?? null);
const userDisplayName = computed(() => userProfile.value?.name?.trim() ?? '');
const userAvatarUrl = computed(() => userProfile.value?.avatar?.trim() ?? '');
const userAvatarText = computed(() => userDisplayName.value.slice(0, 1) || 'U');
const userHandle = computed(() => {
  // 用户名统一展示为 @username 形式。
  const normalized = (userProfile.value?.username ?? '').trim().replace(/^@+/, '');
  return `@${normalized || 'unknown'}`;
});
const isDarkTheme = computed(() => currentThemeValue.value === 'dark');
const collapseTriggerTop = computed(() =>
  Math.max(300 - (showHeader.value ? headerHeight.value : 0), 0)
);

const menuIconRegistry: Record<string, typeof AppsOutline> = {
  apps: AppsOutline,
  layers: LayersOutline
};

const headerAppIconRegistry: Record<string, typeof AppsOutline> = {
  apps: AppsOutline,
  layers: LayersOutline,
  rocket: RocketOutline,
  cube: CubeOutline,
  book: BookOutline,
  cloud: CloudOutline,
  doc: DocumentTextOutline
};

const layoutStyle = computed(() => ({
  '--vk-header-height': `${headerHeight.value}px`
}));

const bodyStyle = computed(() => ({
  height: showHeader.value ? `calc(100vh - ${headerHeight.value}px)` : '100vh'
}));

const headerTitleZoneStyle = computed(() => {
  // Header 标题区不随菜单收缩变化，始终保持展开宽度，避免标题被压缩。
  const width = menuWidth.value;
  return {
    flex: `0 0 ${width}px`,
    width: `${width}px`,
    minWidth: `${width}px`
  };
});

const languageDropdownOptions = computed<DropdownOption[]>(() =>
  (props.languageOptions ?? []).map((item) => ({
    key: item.value,
    label: item.label
  }))
);

const userMenuOptions = computed<DropdownOption[]>(() =>
  (props.userMenus ?? []).map((item) => ({
    key: item.key,
    label: item.label
  }))
);

const menuPathMap = computed<Map<string, VkMenuItem>>(() => {
  const mapping = new Map<string, VkMenuItem>();
  const walk = (items: VkMenuItem[]): void => {
    for (const item of items) {
      if (item.path) {
        mapping.set(item.path, item);
      }
      if (item.children?.length) {
        walk(item.children);
      }
    }
  };
  walk(menuItems.value);
  return mapping;
});

const menuOptions = computed<MenuOption[]>(() => {
  const mapOptions = (items: VkMenuItem[]): MenuOption[] => {
    return items.map((item) => ({
      key: item.key,
      label: item.label,
      icon: renderMenuIcon(item.icon),
      disabled: item.disabled ?? false,
      children: item.children?.length ? mapOptions(item.children) : undefined
    }));
  };
  return mapOptions(menuItems.value);
});

const siderStyle = computed(() => ({
  '--vk-collapse-trigger-top': `${collapseTriggerTop.value}px`
}));

const hasRouter = computed(() => Boolean(resolveRouter()));

watch(
  () => props.collapsed,
  (value) => {
    innerCollapsed.value = value ?? false;
  }
);

watch(
  () => props.currentLanguage,
  (value) => {
    currentLanguageValue.value = value ?? '';
  }
);

watch(
  () => props.currentTheme,
  (value) => {
    currentThemeValue.value = value ?? 'light';
  }
);

watch(
  () => resolveRoute()?.path,
  (path) => {
    if (!path) {
      return;
    }
    // 路由变化时自动同步菜单选中状态。
    const matched = menuPathMap.value.get(path);
    if (matched) {
      selectedKey.value = matched.key;
    }
  },
  { immediate: true }
);

function resolveRouter(): Router | null {
  return (currentInstance?.appContext.config.globalProperties.$router as Router | undefined) ?? null;
}

function resolveRoute(): RouteLocationNormalizedLoaded | null {
  return (currentInstance?.appContext.config.globalProperties.$route as RouteLocationNormalizedLoaded | undefined) ?? null;
}

function findMenuByKey(items: VkMenuItem[], targetKey: string): VkMenuItem | null {
  for (const item of items) {
    if (item.key === targetKey) {
      return item;
    }
    if (item.children?.length) {
      const childMatched = findMenuByKey(item.children, targetKey);
      if (childMatched) {
        return childMatched;
      }
    }
  }
  return null;
}

function toBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'number') {
    return value !== 0;
  }
  if (typeof value === 'string') {
    return ['1', 'true', 'yes', 'y'].includes(value.trim().toLowerCase());
  }
  return false;
}

function pickField(record: Record<string, unknown>, candidates: string[]): unknown {
  for (const candidate of candidates) {
    if (candidate in record) {
      return record[candidate];
    }
  }
  return undefined;
}

function normalizeVkApps(input: unknown, fieldMap: VkAppFieldMap): VkHeaderAppItem[] {
  const keyField = fieldMap.key ?? 'key';
  const labelField = fieldMap.label ?? 'label';
  const iconField = fieldMap.icon ?? 'icon';
  const pathField = fieldMap.path ?? 'path';
  const recommendedField = fieldMap.recommended ?? 'recommended';

  const appList = Array.isArray(input)
    ? input
    : (input as { items?: unknown; list?: unknown; data?: unknown } | null)?.items ??
      (input as { items?: unknown; list?: unknown; data?: unknown } | null)?.list ??
      (input as { items?: unknown; list?: unknown; data?: unknown } | null)?.data;

  if (!Array.isArray(appList)) {
    return [];
  }

  // 将后端任意字段结构映射为统一的 VkHeaderAppItem。
  const normalized: VkHeaderAppItem[] = [];
  appList.forEach((item) => {
    if (typeof item !== 'object' || item === null) {
      return;
    }
    const record = item as Record<string, unknown>;
    const keyValue = pickField(record, [keyField, 'id', 'appId']);
    const labelValue = pickField(record, [labelField, 'name', 'appName']);
    if (keyValue === undefined || labelValue === undefined) {
      return;
    }
    const iconValue = pickField(record, [iconField, 'iconName']);
    const pathValue = pickField(record, [pathField, 'routePath']);
    const recommendedValue = pickField(record, [recommendedField, 'isRecommended']);
    normalized.push({
      key: String(keyValue),
      label: String(labelValue),
      icon: iconValue === undefined || iconValue === null ? undefined : String(iconValue),
      path: pathValue === undefined || pathValue === null ? undefined : String(pathValue),
      recommended: toBoolean(recommendedValue),
      raw: item
    });
  });
  return normalized;
}

async function loadApps(): Promise<void> {
  try {
    let rawApps: unknown;
    if (props.appFetcher) {
      rawApps = await props.appFetcher();
    } else if (apiClient) {
      if (!props.appApiPath) {
        throw new Error('未配置 appApiPath，无法通过 ApiClient 加载应用数据。');
      }
      const response = await apiClient.query<unknown>(props.appApiPath, props.appApiQuery ?? {});
      rawApps = response.data;
    } else {
      rawApps = props.allApps;
    }
    appItems.value = normalizeVkApps(rawApps, props.appFieldMap ?? {});
  } catch (error) {
    // 应用数据加载失败时降级使用静态配置，避免 header 功能完全不可用。
    appItems.value = props.allApps ?? [];
    console.error('[VkAdminLayout] 应用数据加载失败：', error);
  }
}

function resolveInitialApp(): VkHeaderAppItem | null {
  const routePath = resolveRoute()?.path;
  if (routePath) {
    const matched = allApps.value.find((item) => item.path === routePath);
    if (matched) {
      return matched;
    }
  }
  return quickApps.value[0] ?? allApps.value[0] ?? null;
}

async function loadMenu(app?: VkHeaderAppItem | null): Promise<void> {
  loading.value = true;
  menuLoadError.value = null;
  try {
    const currentApp = app ?? activeApp.value;
    const appKey = currentApp?.key;
    let rawMenu: unknown;
    if (props.menuFetcher) {
      rawMenu = await props.menuFetcher(currentApp);
    } else {
      if (!apiClient) {
        throw new Error('未提供 menuFetcher 且未通过插件注入 ApiClient。');
      }
      if (!props.menuApiPath) {
        throw new Error('未配置 menuApiPath，无法通过 ApiClient 加载菜单数据。');
      }
      // 通过统一 ApiClient 拉取菜单，保持与业务接口交互方式一致。
      const query: Record<string, unknown> = { ...(props.menuApiQuery ?? {}) };
      if (appKey) {
        query[props.menuApiAppKey ?? 'appKey'] = appKey;
      }
      const response = await apiClient.query<unknown>(props.menuApiPath, query);
      rawMenu = response.data;
    }

    menuItems.value = normalizeVkMenus(rawMenu, props.menuFieldMap ?? {});
    emit('menu-loaded', menuItems.value);

    const routePath = resolveRoute()?.path;
    if (routePath) {
      const matchedByRoute = menuPathMap.value.get(routePath);
      if (matchedByRoute) {
        selectedKey.value = matchedByRoute.key;
      }
    } else if (props.defaultSelectedKey) {
      selectedKey.value = props.defaultSelectedKey;
    }
  } catch (error) {
    menuLoadError.value = error instanceof Error ? error.message : '未知菜单加载错误';
    emit('menu-load-error', error);
  } finally {
    loading.value = false;
  }
}

function handleExpandedKeys(keys: Array<string | number>): void {
  expandedKeys.value = keys.map((key) => String(key));
}

function handleReloadMenu(): void {
  void loadMenu();
}

function resolveIcon(iconName?: string): typeof AppsOutline {
  if (!iconName) {
    return AppsOutline;
  }
  return menuIconRegistry[iconName] ?? AppsOutline;
}

function renderMenuIcon(iconName?: string): (() => ReturnType<typeof h>) | undefined {
  // 菜单默认带图标，接口未返回 icon 字段时使用统一默认图标。
  const iconComponent = resolveIcon(iconName);
  return () =>
    h(NIcon, null, {
      default: () => h(iconComponent)
    });
}

function resolveHeaderAppIcon(iconName?: string): typeof AppsOutline {
  // Header 应用图标统一通过名称映射，未配置时回退默认图标。
  if (!iconName) {
    return AppsOutline;
  }
  return headerAppIconRegistry[iconName] ?? AppsOutline;
}

function onGlobalKeywordChange(value: string): void {
  globalKeyword.value = value;
}

function handleCollapsedChange(value: boolean): void {
  innerCollapsed.value = value;
  emit('update:collapsed', value);
}

async function jumpByAppPath(path?: string): Promise<void> {
  if (!path) {
    return;
  }
  const router = resolveRouter();
  if (!router) {
    return;
  }
  await router.push(path).catch(() => undefined);
}

async function handleQuickAppClick(app: VkHeaderAppItem): Promise<void> {
  activeAppKey.value = app.key;
  emit('quick-app-click', app);
  await loadMenu(app);
  await jumpByAppPath(app.path);
}

async function handleAllAppClick(app: VkHeaderAppItem): Promise<void> {
  activeAppKey.value = app.key;
  emit('all-app-click', app);
  await loadMenu(app);
  await jumpByAppPath(app.path);
}

function handleGlobalSearch(): void {
  emit('global-search', globalKeyword.value.trim());
}

function handleNotificationClick(): void {
  emit('notification-click');
}

async function handleNotificationItemClick(item: VkNotificationItem): Promise<void> {
  emit('notification-item-click', item);
  if (!item.path) {
    return;
  }
  const router = resolveRouter();
  if (!router) {
    return;
  }
  // 消息项支持可选路由跳转，兼容业务按消息详情路由导航。
  await router.push(item.path).catch(() => undefined);
}

async function handleMessageCenterClick(): Promise<void> {
  emit('message-center-click');
  if (!props.messageCenterPath) {
    return;
  }
  const router = resolveRouter();
  if (!router) {
    return;
  }
  // 底部按钮统一跳转消息中心页面，未配置路由时仅触发事件。
  await router.push(props.messageCenterPath).catch(() => undefined);
}

function handleLanguageChange(value: string): void {
  currentLanguageValue.value = value;
  emit('language-change', value);
}

function handleLanguageSelect(key: string | number): void {
  // 多语言切换入口改为图标下拉，选择后仍复用统一事件。
  handleLanguageChange(String(key));
}

function handleThemeToggle(): void {
  // 通过 Naive UI 的 darkTheme 在组件内部进行明暗皮肤切换。
  const nextTheme: VkThemeMode = currentThemeValue.value === 'dark' ? 'light' : 'dark';
  currentThemeValue.value = nextTheme;
  emit('theme-change', nextTheme);
}

function handleUserMenuSelect(key: string | number): void {
  emit('user-menu-click', String(key));
}

async function handleMenuSelect(key: string | number): Promise<void> {
  const normalizedKey = String(key);
  const menu = findMenuByKey(menuItems.value, normalizedKey);
  if (!menu) {
    return;
  }
  selectedKey.value = normalizedKey;
  emit('menu-click', menu);

  if (!menu.path) {
    return;
  }

  const router = resolveRouter();
  if (!router) {
    return;
  }

  // 菜单点击统一走路由跳转，页面内容区由 RouterView 渲染。
  await router.push(menu.path).catch(() => undefined);
}

onMounted(() => {
  void (async () => {
    await loadApps();
    const initialApp = resolveInitialApp();
    if (initialApp) {
      activeAppKey.value = initialApp.key;
    }
    await loadMenu(initialApp);
  })();
});
</script>

<style scoped>
.vf-vk-admin-layout {
  height: 100vh;
  background: #f5f7fb;
}

.vf-vk-admin-layout--dark {
  background: #18181c;
}

.vf-vk-admin-layout__header {
  height: var(--vk-header-height);
  padding: 0;
  display: flex;
  align-items: center;
  background: var(--n-color);
}

.vf-vk-admin-layout__header-brand {
  display: flex;
  align-items: center;
  height: 100%;
  padding: 0 20px;
  box-sizing: border-box;
}

.vf-vk-admin-layout__header-brand-inner {
  display: inline-flex;
  align-items: center;
}

.vf-vk-admin-layout__header-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  box-sizing: border-box;
}

.vf-vk-admin-layout__quick-app-btn {
  min-width: 56px;
}

.vf-vk-admin-layout__quick-app-btn--active {
  background: var(--n-action-color, #f3f4f6);
}

.vf-vk-admin-layout__header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
  padding-right: 16px;
  box-sizing: border-box;
}

.vf-vk-admin-layout__all-apps-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.vf-vk-admin-layout__all-apps-icon {
  margin-top: 1px;
}

.vf-vk-admin-layout__all-apps-panel {
  width: 356px;
  padding: 12px;
}

.vf-vk-admin-layout__all-apps-title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color, #111827);
}

.vf-vk-admin-layout__all-apps-tile {
  width: 100%;
  height: 74px;
}

.vf-vk-admin-layout__all-apps-tile--active {
  background: var(--n-action-color, #f3f4f6);
}

.vf-vk-admin-layout__all-apps-tile :deep(.n-button__content) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.vf-vk-admin-layout__all-apps-tile-icon {
  font-size: 24px;
}

.vf-vk-admin-layout__all-apps-tile-label {
  font-size: 12px;
  line-height: 1;
}

.vf-vk-admin-layout__global-search {
  width: 200px;
  max-width: 40%;
  margin-left: 8px;
}

.vf-vk-admin-layout__title {
  margin: 0;
  font-size: 16px;
  line-height: 1;
  font-weight: 600;
  color: var(--n-text-color);
}

.vf-vk-admin-layout__user-panel {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.vf-vk-admin-layout__user-meta {
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  min-width: 0;
}

.vf-vk-admin-layout__user-name {
  max-width: 104px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.2;
}

.vf-vk-admin-layout__user-handle {
  max-width: 104px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  line-height: 1.1;
  color: var(--n-text-color-3, #6b7280);
}

.vf-vk-admin-layout__notice-panel {
  width: 320px;
}

.vf-vk-admin-layout__notice-body {
  padding: 12px;
}

.vf-vk-admin-layout__notice-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--n-text-color, #111827);
  margin-bottom: 8px;
}

.vf-vk-admin-layout__notice-empty {
  padding: 8px 0;
}

.vf-vk-admin-layout__notice-list {
  max-height: 280px;
  overflow: auto;
}

.vf-vk-admin-layout__notice-item {
  padding: 10px 8px;
  border-radius: var(--n-border-radius, 6px);
  cursor: pointer;
}

.vf-vk-admin-layout__notice-item:hover {
  background: var(--n-action-color, #f3f4f6);
}

.vf-vk-admin-layout__notice-item + .vf-vk-admin-layout__notice-item {
  margin-top: 4px;
}

.vf-vk-admin-layout__notice-item-main {
  min-width: 0;
}

.vf-vk-admin-layout__notice-item-title {
  font-size: 13px;
  line-height: 1.4;
  color: var(--n-text-color, #111827);
}

.vf-vk-admin-layout__notice-item-summary {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--n-text-color-3, #6b7280);
}

.vf-vk-admin-layout__notice-item-time {
  margin-top: 4px;
  font-size: 12px;
  color: var(--n-text-color-3, #6b7280);
}

.vf-vk-admin-layout__notice-footer {
  border-top: 1px solid var(--n-border-color, #e5e7eb);
}

.vf-vk-admin-layout__notice-footer-btn {
  width: 100%;
  height: 40px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--n-text-color, #111827);
  font-size: 14px;
  cursor: pointer;
  transition: background-color 0.2s ease;
}

.vf-vk-admin-layout__notice-footer-btn:hover {
  background: var(--n-action-color, #f3f4f6);
}

.vf-vk-admin-layout__notice-footer-btn:focus-visible {
  outline: 2px solid var(--n-primary-color, #18a058);
  outline-offset: -2px;
}

.vf-vk-admin-layout__notice-footer-btn:active {
  background: var(--n-action-color, #eef2f7);
}

.vf-vk-admin-layout__notice-footer-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 1200px) {
  .vf-vk-admin-layout__global-search {
    width: 220px;
  }
}

.vf-vk-admin-layout__sider {
  position: relative;
}

.vf-vk-admin-layout__sider :deep(.n-layout-toggle-button) {
  top: var(--vk-collapse-trigger-top);
  transform: translateX(50%);
}

.vf-vk-admin-layout__menu-scroll {
  height: 100%;
}

.vf-vk-admin-layout__state {
  min-height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
}

.vf-vk-admin-layout__content {
  padding: 20px;
  overflow: auto;
}
</style>
