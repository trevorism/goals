<script setup>
import { computed, onMounted, ref } from 'vue'
import { errorMessage, observationsApi, todayApi } from '../api.js'
import { formatDay, toDay } from '../forms.js'
import ObservationEntry from '../components/ObservationEntry.vue'

const items = ref([])
const loading = ref(true)
const saving = ref(null)
const error = ref('')
const today = ref(new Date())

const groups = computed(() => {
  const byRoot = new Map()
  items.value.forEach((item) => {
    const group = byRoot.get(item.rootId) ?? { rootId: item.rootId, rootTitle: item.rootTitle, items: [] }
    group.items.push(item)
    byRoot.set(item.rootId, group)
  })
  return [...byRoot.values()]
})

const periodLabel = { daily: 'today', weekly: 'this week', monthly: 'this month' }

function describeItem(item) {
  const parts = []
  if (item.goalTitle !== item.rootTitle) parts.push(item.goalTitle)
  parts.push(`due ${periodLabel[item.metric.frequency] ?? 'today'}`)
  if (item.metric.lastObservedAt) parts.push(`last ${formatDay(item.metric.lastObservedAt)}`)
  return parts.join(' · ')
}

async function load() {
  loading.value = true
  try {
    today.value = new Date()
    items.value = await todayApi.due(toDay(today.value))
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function record(item, payload) {
  saving.value = item.metric.id
  error.value = ''
  try {
    await observationsApi.create(item.metric.id, payload)
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = null
  }
}

onMounted(load)
</script>

<template>
  <section class="today flex flex-col gap-4">
    <div class="flex items-baseline justify-between">
      <h1 class="text-2xl font-semibold">Today</h1>
      <span class="today-date text-sm text-slate-500">{{ formatDay(toDay(today) + 'T00:00:00Z') }}</span>
    </div>
    <p v-if="error" class="page-error text-red-600">{{ error }}</p>
    <p v-if="loading && !items.length" class="text-slate-500">Loading…</p>
    <p v-else-if="!items.length" class="all-done text-slate-500">Nothing is due. Every metric has a value for its current day, week or month.</p>

    <section v-for="group in groups" :key="group.rootId" class="today-group flex flex-col gap-3">
      <h2 class="text-lg font-semibold">
        <router-link :to="{ name: 'goal', params: { id: group.rootId } }" class="group-link">{{ group.rootTitle }}</router-link>
      </h2>
      <va-card v-for="item in group.items" :key="item.metric.id" class="today-item">
        <va-card-content class="flex flex-col gap-2">
          <div class="flex flex-wrap items-baseline justify-between gap-2">
            <span class="item-name font-semibold">{{ item.metric.name }}</span>
            <span class="item-context text-sm text-slate-500">{{ describeItem(item) }}</span>
          </div>
          <observation-entry :metric="item.metric" :saving="saving === item.metric.id" @record="record(item, $event)" />
        </va-card-content>
      </va-card>
    </section>
  </section>
</template>
