<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage, goalsApi } from '../api.js'
import { formatDay } from '../forms.js'
import GoalForm from '../components/GoalForm.vue'
import StatusChip from '../components/StatusChip.vue'

const router = useRouter()
const goals = ref([])
const loading = ref(true)
const creating = ref(false)
const saving = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  try {
    goals.value = await goalsApi.listRoots()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    loading.value = false
  }
}

async function create(payload) {
  saving.value = true
  error.value = ''
  try {
    const goal = await goalsApi.createRoot(payload)
    router.push({ name: 'goal', params: { id: goal.id } })
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="goal-list flex flex-col gap-4">
    <div class="flex items-center justify-between">
      <h1 class="text-2xl font-semibold">Goals</h1>
      <va-button v-if="!creating" @click="creating = true">New goal</va-button>
    </div>

    <va-card v-if="creating" class="new-goal">
      <va-card-title>New goal</va-card-title>
      <va-card-content>
        <goal-form require-dates submit-label="Create goal" :saving="saving" @save="create" @cancel="creating = false" />
      </va-card-content>
    </va-card>

    <p v-if="error" class="page-error text-red-600">{{ error }}</p>
    <p v-if="loading" class="text-slate-500">Loading…</p>
    <p v-else-if="!goals.length && !creating" class="empty-state text-slate-500">
      No goals yet. Create one to start breaking it down and tracking it.
    </p>

    <div class="grid gap-4 md:grid-cols-2">
      <router-link v-for="goal in goals" :key="goal.id" :to="{ name: 'goal', params: { id: goal.id } }" class="goal-card no-underline">
        <va-card class="h-full hover:shadow-md">
          <va-card-title class="flex items-center justify-between gap-2">
            <span class="text-lg">{{ goal.title }}</span>
            <status-chip :status="goal.status" />
          </va-card-title>
          <va-card-content>
            <p v-if="goal.description" class="mb-2 text-slate-600">{{ goal.description }}</p>
            <p class="text-sm text-slate-500">{{ formatDay(goal.startDate) }} – {{ formatDay(goal.endDate) }}</p>
          </va-card-content>
        </va-card>
      </router-link>
    </div>
  </section>
</template>
