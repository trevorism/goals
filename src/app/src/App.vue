<script setup>
import { RouterView } from 'vue-router'
import { MenuBar } from '@trevorism/ui-header-bar'
</script>

<template>
  <menu-bar></menu-bar>
  <main class="mx-auto max-w-6xl p-4">
    <router-view></router-view>
  </main>
</template>
