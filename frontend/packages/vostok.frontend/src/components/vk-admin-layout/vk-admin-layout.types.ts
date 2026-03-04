export interface VkMenuItem {
  key: string;
  label: string;
  path?: string;
  icon?: string;
  disabled?: boolean;
  children?: VkMenuItem[];
  raw?: unknown;
}

export interface VkMenuFieldMap {
  key?: string;
  label?: string;
  path?: string;
  icon?: string;
  disabled?: string;
  children?: string;
}

export interface VkHeaderAppItem {
  key: string;
  label: string;
  icon?: string;
  path?: string;
  recommended?: boolean;
  raw?: unknown;
}

export interface VkAppFieldMap {
  key?: string;
  label?: string;
  icon?: string;
  path?: string;
  recommended?: string;
}

export interface VkLanguageOption {
  value: string;
  label: string;
}

export interface VkUserMenuItem {
  key: string;
  label: string;
}

export interface VkUserProfile {
  id: string;
  avatar?: string;
  name: string;
  username: string;
}

export interface VkNotificationItem {
  key: string;
  title: string;
  time?: string;
  summary?: string;
  path?: string;
}

export type VkThemeMode = 'light' | 'dark';

export interface VkAdminLayoutProps {
  title?: string;
  menuFetcher?: (app?: VkHeaderAppItem | null) => Promise<unknown>;
  menuApiPath?: string;
  menuApiQuery?: Record<string, unknown>;
  menuApiAppKey?: string;
  menuFieldMap?: VkMenuFieldMap;
  appFetcher?: () => Promise<unknown>;
  appApiPath?: string;
  appApiQuery?: Record<string, unknown>;
  appFieldMap?: VkAppFieldMap;
  recommendAppCount?: number;
  defaultExpandedKeys?: string[];
  defaultSelectedKey?: string;
  showHeader?: boolean;
  headerHeight?: number;
  menuWidth?: number;
  menuCollapsedWidth?: number;
  quickApps?: VkHeaderAppItem[];
  allApps?: VkHeaderAppItem[];
  languageOptions?: VkLanguageOption[];
  currentLanguage?: string;
  currentTheme?: VkThemeMode;
  notificationCount?: number;
  recentNotifications?: VkNotificationItem[];
  messageCenterPath?: string;
  messageCenterButtonText?: string;
  user?: VkUserProfile;
  userMenus?: VkUserMenuItem[];
  collapsed?: boolean;
  collapsible?: boolean;
}
