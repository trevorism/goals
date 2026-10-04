<script setup>
import { watch } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { MenuBar } from '@trevorism/ui-header-bar'
import { useAuth } from '@trevorism/ui-auth'
import { syncTimezone } from './api.js'

const { isAuthenticated } = useAuth()

watch(
  isAuthenticated,
  (authenticated) => {
    if (authenticated) {
      syncTimezone().catch(() => {})
    }
  },
  { immediate: true }
)
</script>

<template>
  <menu-bar></menu-bar>
  <nav class="app-nav mx-auto flex max-w-6xl gap-4 px-4 pt-4 text-sm">
    <router-link :to="{ name: 'goals' }" class="nav-link text-slate-600" exact-active-class="font-semibold text-slate-900">Goals</router-link>
    <router-link :to="{ name: 'today' }" class="nav-link text-slate-600" exact-active-class="font-semibold text-slate-900">Today</router-link>
    <router-link :to="{ name: 'settings' }" class="nav-link text-slate-600" exact-active-class="font-semibold text-slate-900">Settings</router-link>
  </nav>
  <main class="mx-auto max-w-6xl p-4">
    <router-view></router-view>
  </main>
</template>
