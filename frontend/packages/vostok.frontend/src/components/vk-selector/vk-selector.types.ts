export type VkSelectorPrimitive = string | number;
export type VkSelectorValue = VkSelectorPrimitive | VkSelectorPrimitive[] | null;
export type VkSelectorMode = 'single' | 'multiple';
export type VkSelectorOptionType = 'flat' | 'tree';

export interface VkSelectorOption {
  label: string;
  value: VkSelectorPrimitive;
  disabled?: boolean;
}

export interface VkSelectorTreeOption {
  label: string;
  key: VkSelectorPrimitive;
  disabled?: boolean;
  children?: VkSelectorTreeOption[];
}

export interface VkSelectorRecentItem {
  label: string;
  value: VkSelectorPrimitive;
}

export interface VkSelectorProps {
  modelValue?: VkSelectorValue;
  mode?: VkSelectorMode;
  optionType?: VkSelectorOptionType;
  selectorName?: string;
  options?: VkSelectorOption[];
  treeOptions?: VkSelectorTreeOption[];
  searchable?: boolean;
  clearable?: boolean;
  disabled?: boolean;
  showRecent?: boolean;
  recentLimit?: number;
  recentTitle?: string;
  recentStorageKey?: string;
}
