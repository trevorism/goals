<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, errorMessage, goalsApi } from '../api.js'
import { formatDay, toDay } from '../forms.js'
import { describeHeadline, describeNeedsYou, formatPercent } from '../progress.js'
import { GoalStatusType } from '../types.js'
import GoalForm from '../components/GoalForm.vue'
import ProgressMeter from '../components/ProgressMeter.vue'
import ProgressStatus from '../components/ProgressStatus.vue'
import StatusChip from '../components/StatusChip.vue'

const router = useRouter()
const dashboard = ref({ goals: [], needsYou: [] })
const loading = ref(true)
const creating = ref(false)
const saving = ref(false)
const error = ref('')

const activeRows = computed(() => dashboard.value.goals.filter((row) => row.goal.status === GoalStatusType.ACTIVE))
const closedRows = computed(() => dashboard.value.goals.filter((row) => row.goal.status !== GoalStatusType.ACTIVE))

const planMarker = (progress) =>
  progress?.progress !== null && progress?.progress !== undefined && progress?.pace !== null && progress?.pace !== undefined
    ? progress.progress - progress.pace
    : null

async function load() {
  loading.value = true
  try {
    dashboard.value = await dashboardApi.get(toDay(new Date()))
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

    <section v-if="dashboard.needsYou.length" class="needs-you rounded border border-slate-200 bg-slate-50 p-3">
      <h2 class="mb-1 text-sm font-semibold text-slate-700">Needs you</h2>
      <ul class="flex flex-col gap-1 text-sm">
        <li v-for="item in dashboard.needsYou" :key="`${item.type}-${item.metricId ?? item.goalId}-${item.date}`" class="needs-you-item">
          <router-link :to="{ name: 'goal', params: { id: item.rootId } }" class="text-slate-700">{{ describeNeedsYou(item) }}</router-link>
        </li>
      </ul>
    </section>

    <p v-if="loading && !dashboard.goals.length" class="text-slate-500">Loading…</p>
    <p v-else-if="!dashboard.goals.length && !creating" class="empty-state text-slate-500">
      No goals yet. Create one to start breaking it down and tracking it.
    </p>

    <div class="flex flex-col gap-3">
      <router-link
        v-for="row in activeRows"
        :key="row.goal.id"
        :to="{ name: 'goal', params: { id: row.goal.id } }"
        class="goal-card no-underline"
      >
        <va-card class="hover:shadow-md">
          <va-card-content class="grid gap-3 md:grid-cols-[1fr_16rem] md:items-center">
            <div class="flex flex-col gap-1">
              <div class="flex flex-wrap items-center gap-3">
                <span class="goal-name text-lg font-semibold text-slate-900">{{ row.goal.title }}</span>
                <progress-status :status="row.progress?.status ?? 'no_data'" />
              </div>
              <span class="headline text-sm text-slate-600">{{ describeHeadline(row) }}</span>
              <span class="time-left text-xs text-slate-500">
                {{ formatPercent(row.progress?.expected) }} of the time has passed · ends {{ formatDay(row.goal.endDate) }}
              </span>
            </div>
            <progress-meter
              v-if="row.progress?.progress !== null && row.progress?.progress !== undefined"
              class="goal-meter"
              label="Progress"
              :value="row.progress.progress"
              :expected="planMarker(row.progress)"
            />
          </va-card-content>
        </va-card>
      </router-link>
    </div>

    <section v-if="closedRows.length" class="closed-goals flex flex-col gap-1">
      <h2 class="text-sm font-semibold text-slate-500">Closed</h2>
      <router-link
        v-for="row in closedRows"
        :key="row.goal.id"
        :to="{ name: 'goal', params: { id: row.goal.id } }"
        class="closed-goal flex items-center gap-2 text-sm text-slate-600"
      >
        <span>{{ row.goal.title }}</span>
        <status-chip :status="row.goal.status" />
      </router-link>
    </section>
  </section>
</template>
