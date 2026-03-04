import type { VkMenuFieldMap, VkMenuItem } from './vk-admin-layout.types';

const FALLBACK_FIELD_MAP: Required<VkMenuFieldMap> = {
  key: 'key',
  label: 'label',
  path: 'path',
  icon: 'icon',
  disabled: 'disabled',
  children: 'children'
};

function isRecord(input: unknown): input is Record<string, unknown> {
  return typeof input === 'object' && input !== null && !Array.isArray(input);
}

function pickFieldValue(
  item: Record<string, unknown>,
  primaryKey: string,
  fallbackKeys: string[]
): unknown {
  if (primaryKey in item) {
    return item[primaryKey];
  }
  for (const key of fallbackKeys) {
    if (key in item) {
      return item[key];
    }
  }
  return undefined;
}

function extractMenuList(raw: unknown): unknown[] {
  if (Array.isArray(raw)) {
    return raw;
  }
  if (!isRecord(raw)) {
    return [];
  }

  // 兼容常见后端返回结构，减少业务项目重复写转换逻辑。
  const candidates = [raw.list, raw.items, raw.menus, raw.data];
  for (const candidate of candidates) {
    if (Array.isArray(candidate)) {
      return candidate;
    }
    if (isRecord(candidate) && Array.isArray(candidate.items)) {
      return candidate.items;
    }
  }
  return [];
}

function normalizeMenuItem(
  input: unknown,
  fieldMap: Required<VkMenuFieldMap>,
  index: number
): VkMenuItem | null {
  if (!isRecord(input)) {
    return null;
  }

  const keyValue = pickFieldValue(input, fieldMap.key, ['id', 'menuId', 'code', 'path']);
  const labelValue = pickFieldValue(input, fieldMap.label, ['name', 'title', 'menuName']);
  const pathValue = pickFieldValue(input, fieldMap.path, ['routePath', 'url']);
  const iconValue = pickFieldValue(input, fieldMap.icon, ['iconName']);
  const disabledValue = pickFieldValue(input, fieldMap.disabled, ['isDisabled']);
  const childrenValue = pickFieldValue(input, fieldMap.children, ['subMenus', 'items', 'children']);

  const key = String(keyValue ?? pathValue ?? `menu-${index}`);
  const label = String(labelValue ?? key);
  const path = pathValue == null ? undefined : String(pathValue);
  const icon = iconValue == null ? undefined : String(iconValue);
  const disabled = Boolean(disabledValue ?? false);

  const childrenRaw = Array.isArray(childrenValue) ? childrenValue : [];
  const children = childrenRaw
    .map((child, childIndex) => normalizeMenuItem(child, fieldMap, childIndex))
    .filter((child): child is VkMenuItem => child !== null);

  return {
    key,
    label,
    path,
    icon,
    disabled,
    children: children.length > 0 ? children : undefined,
    raw: input
  };
}

export function normalizeVkMenus(
  raw: unknown,
  fieldMap: VkMenuFieldMap = {}
): VkMenuItem[] {
  const mergedFieldMap: Required<VkMenuFieldMap> = {
    ...FALLBACK_FIELD_MAP,
    ...fieldMap
  };
  return extractMenuList(raw)
    .map((item, index) => normalizeMenuItem(item, mergedFieldMap, index))
    .filter((item): item is VkMenuItem => item !== null);
}
