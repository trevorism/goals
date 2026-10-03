<script setup>
import { computed, onMounted, ref } from 'vue'
import { errorMessage, metricsApi, observationsApi } from '../api.js'
import { describeObservation, describeTarget, formatDay } from '../forms.js'
import { metricTypeLabels } from '../types.js'
import MetricForm from './MetricForm.vue'
import ObservationEntry from './ObservationEntry.vue'

const RECENT_COUNT = 10

const props = defineProps({
  metric: { type: Object, required: true }
})

const emit = defineEmits(['changed'])

const observations = ref([])
const editing = ref(false)
const saving = ref(false)
const error = ref('')

const recent = computed(() => [...observations.value].reverse().slice(0, RECENT_COUNT))
const target = computed(() => describeTarget(props.metric))

async function loadObservations() {
  try {
    observations.value = await observationsApi.list(props.metric.id)
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function record(payload) {
  saving.value = true
  error.value = ''
  try {
    await observationsApi.create(props.metric.id, payload)
    await loadObservations()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

async function removeObservation(observation) {
  if (!window.confirm('Delete this value?')) {
    return
  }
  try {
    await observationsApi.remove(observation.id)
    await loadObservations()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function save(payload) {
  saving.value = true
  error.value = ''
  try {
    await metricsApi.update(props.metric.id, payload)
    editing.value = false
    emit('changed')
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

async function removeMetric() {
  if (!window.confirm(`Delete "${props.metric.name}" and all of its values?`)) {
    return
  }
  try {
    await metricsApi.remove(props.metric.id)
    emit('changed')
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(loadObservations)
</script>

<template>
  <va-card class="metric-card">
    <va-card-title class="flex items-center justify-between gap-2">
      <span class="metric-name text-base">{{ metric.name }}</span>
      <span class="flex items-center gap-1">
        <va-chip size="small" outline color="secondary">{{ metricTypeLabels[metric.type] }}</va-chip>
        <va-chip size="small" outline :color="metric.measures === 'effort' ? 'warning' : 'info'">{{ metric.measures }}</va-chip>
      </span>
    </va-card-title>
    <va-card-content class="flex flex-col gap-3">
      <metric-form v-if="editing" :metric="metric" :saving="saving" @save="save" @cancel="editing = false" />
      <template v-else>
        <p class="metric-summary text-sm text-slate-500">
          {{ metric.frequency }}<span v-if="target"> · target {{ target }}</span>
        </p>
        <observation-entry :metric="metric" :saving="saving" @record="record" />
        <p v-if="error" class="card-error text-sm text-red-600">{{ error }}</p>
        <ul v-if="recent.length" class="recent flex flex-col gap-1 text-sm">
          <li v-for="observation in recent" :key="observation.id" class="observation flex items-center gap-2">
            <span class="w-28 text-slate-500">{{ formatDay(observation.observedAt) }}</span>
            <span class="observation-value flex-1">
              {{ describeObservation(metric, observation) }}
              <span v-if="observation.note" class="text-slate-500"> — {{ observation.note }}</span>
            </span>
            <va-button preset="plain" size="small" color="danger" @click="removeObservation(observation)">Delete</va-button>
          </li>
        </ul>
        <p v-else class="no-values text-sm text-slate-500">No values recorded yet.</p>
        <div class="flex gap-2">
          <va-button preset="secondary" size="small" @click="editing = true">Edit metric</va-button>
          <va-button preset="secondary" size="small" color="danger" @click="removeMetric">Delete metric</va-button>
        </div>
      </template>
    </va-card-content>
  </va-card>
</template>
