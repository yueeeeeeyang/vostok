<template>
  <main class="page">
    <h2>VkAdminLayout 使用方式</h2>
    <p class="desc">用于快速搭建后台页框架，包含头部、侧边菜单与内容区路由承载。</p>

    <section class="usage">
      <h3>使用方式</h3>
      <pre><code>import { VkAdminLayout } from '@vostok/frontend';

&lt;VkAdminLayout
  title="业务后台"
  menu-api-path="/menus"
  :menu-field-map="{
    key: 'menuId',
    label: 'menuName',
    path: 'routePath',
    children: 'subMenus'
  }"
  :menu-width="260"
  :menu-collapsed-width="64"
/&gt;</code></pre>
    </section>

    <ul class="tips">
      <li>菜单数据默认通过统一 ApiClient 请求后端接口。</li>
      <li>菜单收缩状态通过 Naive UI 官方 `collapsed` 属性控制。</li>
      <li>点击菜单后通过 `router.push(path)` 切换中央内容区路由。</li>
    </ul>
  </main>
</template>

<script setup lang="ts"></script>

<style scoped>
.page {
  max-width: 920px;
  margin: 0 auto;
}

.desc {
  color: #4b5563;
}

.usage {
  margin-top: 16px;
}

pre {
  margin: 0;
  padding: 12px;
  border-radius: 8px;
  background: #111827;
  color: #e5e7eb;
  overflow: auto;
}

.tips {
  margin-top: 16px;
  padding-left: 20px;
  color: #374151;
}
</style>
