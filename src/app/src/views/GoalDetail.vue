<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { errorMessage, goalsApi, metricsApi } from '../api.js'
import { findNode, formatDay } from '../forms.js'
import { formatPercent, progressById } from '../progress.js'
import { GoalStatusType } from '../types.js'
import AdjustmentLog from '../components/AdjustmentLog.vue'
import GoalForm from '../components/GoalForm.vue'
import GoalTree from '../components/GoalTree.vue'
import MetricCard from '../components/MetricCard.vue'
import MetricForm from '../components/MetricForm.vue'
import ProgressMeter from '../components/ProgressMeter.vue'
import ProgressStatus from '../components/ProgressStatus.vue'
import StatusChip from '../components/StatusChip.vue'

const props = defineProps({
  id: { type: String, required: true }
})

const router = useRouter()
const tree = ref(null)
const progress = ref(null)
const selectedId = ref(props.id)
const mode = ref('view')
const saving = ref(false)
const error = ref('')

const selected = computed(() => findNode(tree.value, selectedId.value))
const selectedGoal = computed(() => selected.value?.goal)
const selectedMetrics = computed(() => selected.value?.metrics ?? [])
const isTreeRoot = computed(() => selectedGoal.value?.id === tree.value?.goal.id)
const progressMaps = computed(() => progressById(progress.value))
const selectedProgress = computed(() => progressMaps.value.goals[selectedId.value] ?? null)
const headlineExpected = computed(() => {
  const goalProgress = selectedProgress.value
  return goalProgress?.progress != null && goalProgress?.pace != null ? goalProgress.progress - goalProgress.pace : null
})
const outcomeIsHeadline = computed(() => selectedProgress.value?.outcomeProgress != null)

async function loadProgress() {
  try {
    progress.value = await goalsApi.progress(props.id)
  } catch (e) {
    progress.value = null
  }
}

async function load() {
  try {
    const [loadedTree] = await Promise.all([goalsApi.tree(props.id), loadProgress()])
    tree.value = loadedTree
    if (!findNode(tree.value, selectedId.value)) {
      selectedId.value = tree.value.goal.id
    }
  } catch (e) {
    error.value = errorMessage(e)
  }
}

function select(id) {
  selectedId.value = id
  mode.value = 'view'
  error.value = ''
}

async function run(action) {
  saving.value = true
  error.value = ''
  try {
    await action()
    mode.value = 'view'
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

const saveGoal = (payload) => run(() => goalsApi.update(selectedId.value, payload))

const addChild = (payload) =>
  run(async () => {
    const child = await goalsApi.createChild(selectedId.value, payload)
    selectedId.value = child.id
  })

const addMetric = (payload) => run(() => metricsApi.create(selectedId.value, payload))

const setStatus = (status) => run(() => goalsApi.update(selectedId.value, { status }))

async function removeGoal() {
  const goal = selectedGoal.value
  if (!window.confirm(`Delete "${goal.title}", everything under it, and all of their values? This can't be undone.`)) {
    return
  }
  if (isTreeRoot.value) {
    try {
      await goalsApi.remove(goal.id)
      router.push({ name: 'goals' })
    } catch (e) {
      error.value = errorMessage(e)
    }
    return
  }
  await run(async () => {
    await goalsApi.remove(goal.id)
    selectedId.value = goal.parentId
  })
}

watch(
  () => props.id,
  (id) => {
    selectedId.value = id
    load()
  }
)

onMounted(load)
</script>

<template>
  <section class="goal-detail flex flex-col gap-4">
    <router-link :to="{ name: 'goals' }" class="text-sm text-slate-500">← All goals</router-link>
    <p v-if="error" class="page-error text-red-600">{{ error }}</p>

    <div v-if="tree" class="grid gap-4 md:grid-cols-[18rem_1fr]">
      <aside class="rounded border border-slate-200 p-2">
        <goal-tree :node="tree" :selected-id="selectedId" :progress-by-goal="progressMaps.goals" @select="select" />
      </aside>

      <div v-if="selectedGoal" :key="selectedGoal.id" class="flex flex-col gap-4">
        <va-card>
          <va-card-content class="flex flex-col gap-3">
            <goal-form
              v-if="mode === 'edit'"
              :goal="selectedGoal"
              :require-dates="isTreeRoot"
              show-status
              :saving="saving"
              @save="saveGoal"
              @cancel="mode = 'view'"
            />
            <template v-else>
              <div class="flex items-start justify-between gap-2">
                <h1 class="goal-title text-2xl font-semibold">{{ selectedGoal.title }}</h1>
                <status-chip :status="selectedGoal.status" />
              </div>
              <p class="text-sm text-slate-500">{{ formatDay(selectedGoal.startDate) }} – {{ formatDay(selectedGoal.endDate) }}</p>
              <div v-if="selectedProgress" class="goal-progress flex flex-col gap-3 rounded border border-slate-200 p-3">
                <div class="flex flex-wrap items-center gap-3">
                  <progress-status :status="selectedProgress.status" />
                  <span class="time-elapsed text-sm text-slate-500">{{ formatPercent(selectedProgress.expected) }} of the time has passed</span>
                </div>
                <div v-if="selectedProgress.progress != null" class="grid gap-3 sm:grid-cols-2">
                  <progress-meter
                    v-if="selectedProgress.outcomeProgress != null"
                    class="outcome-meter"
                    label="Outcome"
                    :value="selectedProgress.outcomeProgress"
                    :expected="headlineExpected"
                  />
                  <progress-meter
                    v-if="selectedProgress.effortProgress != null"
                    class="effort-meter"
                    label="Effort"
                    :value="selectedProgress.effortProgress"
                    :expected="outcomeIsHeadline ? null : headlineExpected"
                  />
                </div>
                <p v-else class="no-progress text-sm text-slate-500">Add a metric with a target, or a sub-goal, to measure progress.</p>
                <p v-if="headlineExpected != null" class="text-xs text-slate-500">The marker shows where the plan says you should be by now.</p>
              </div>
              <p v-if="selectedGoal.description" class="whitespace-pre-line">{{ selectedGoal.description }}</p>
              <p v-if="selectedGoal.definitionOfDone" class="definition-of-done text-sm">
                <b>Done when:</b> {{ selectedGoal.definitionOfDone }}
              </p>
              <div class="flex flex-wrap gap-2">
                <va-button size="small" @click="mode = 'edit'">Edit</va-button>
                <va-button size="small" preset="secondary" @click="mode = 'child'">Add sub-goal</va-button>
                <va-button
                  v-if="selectedGoal.status === GoalStatusType.ACTIVE"
                  size="small"
                  preset="secondary"
                  color="success"
                  @click="setStatus(GoalStatusType.COMPLETED)"
                >
                  Mark completed
                </va-button>
                <va-button
                  v-if="selectedGoal.status === GoalStatusType.ACTIVE"
                  size="small"
                  preset="secondary"
                  @click="setStatus(GoalStatusType.ABANDONED)"
                >
                  Abandon
                </va-button>
                <va-button
                  v-if="selectedGoal.status !== GoalStatusType.ACTIVE"
                  size="small"
                  preset="secondary"
                  @click="setStatus(GoalStatusType.ACTIVE)"
                >
                  Reopen
                </va-button>
                <va-button size="small" preset="secondary" color="danger" @click="removeGoal">Delete</va-button>
              </div>
            </template>
          </va-card-content>
        </va-card>

        <va-card v-if="mode === 'child'" class="new-child">
          <va-card-title>New sub-goal of “{{ selectedGoal.title }}”</va-card-title>
          <va-card-content>
            <p class="mb-2 text-sm text-slate-500">Leave the dates empty to use the parent's dates.</p>
            <goal-form submit-label="Add sub-goal" :saving="saving" @save="addChild" @cancel="mode = 'view'" />
          </va-card-content>
        </va-card>

        <section class="metrics flex flex-col gap-3">
          <div class="flex items-center justify-between">
            <h2 class="text-lg font-semibold">Metrics</h2>
            <va-button v-if="mode !== 'metric'" size="small" preset="secondary" @click="mode = 'metric'">Add metric</va-button>
          </div>
          <va-card v-if="mode === 'metric'" class="new-metric">
            <va-card-content>
              <metric-form :saving="saving" @save="addMetric" @cancel="mode = 'view'" />
            </va-card-content>
          </va-card>
          <metric-card
            v-for="metric in selectedMetrics"
            :key="metric.id"
            :metric="metric"
            :goal="selectedGoal"
            :metric-progress="progressMaps.metrics[metric.id] ?? null"
            :now="progress?.asOf ?? null"
            :segments="progressMaps.segments[metric.id] ?? []"
            @changed="load"
            @recorded="loadProgress"
          />
          <p v-if="!selectedMetrics.length && mode !== 'metric'" class="no-metrics text-sm text-slate-500">
            No metrics yet. Add one to measure this goal.
          </p>
        </section>

        <adjustment-log :goal-id="selectedGoal.id" :metrics="selectedMetrics" @changed="loadProgress" />
      </div>
    </div>
  </section>
</template>
