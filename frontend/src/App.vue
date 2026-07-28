<template>
  <div id="app-container">
    <AppHeader />
    <div class="app-body">
      <aside class="app-sidebar-wrapper">
        <AppSidebar />
      </aside>
      <main class="app-main">
        <router-view />
      </main>
    </div>
    <AppFooter />
  </div>
</template>

<script setup>
import AppHeader from '@/components/AppHeader.vue'
import AppSidebar from '@/components/AppSidebar.vue'
import AppFooter from '@/components/AppFooter.vue'
</script>

<style>
#app-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
}

.app-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.app-sidebar-wrapper {
  width: 220px;
  overflow-y: auto;
}

.app-main {
  flex: 1;
  padding: 20px;
  overflow-y: auto;
  background-color: #f0f2f5;
}
</style>
