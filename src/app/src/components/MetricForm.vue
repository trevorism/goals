<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { measurementsApi } from '../api.js'
import { buildMetricPayload, metricFormFrom, validateMetric } from '../forms.js'
import { FrequencyType, MetricDirectionType, MetricMeasuresType, MetricSourceType, MetricType, metricTypeLabels } from '../types.js'

const props = defineProps({
  metric: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'cancel'])

const form = reactive(metricFormFrom(props.metric))
const error = ref('')
const editing = computed(() => !!props.metric)

const typeOptions = MetricType.ALL.map((value) => ({ value, text: metricTypeLabels[value] }))
const measuresOptions = [
  { value: MetricMeasuresType.OUTCOME, text: 'Outcome — the result you want' },
  { value: MetricMeasuresType.EFFORT, text: 'Effort — the work toward it' }
]

const measurements = ref([])

const sourceOptions = computed(() => {
  const options = [
    { value: MetricSourceType.MANUAL, text: 'I enter it myself' }
  ]
  if (measurements.value.length || form.source === MetricSourceType.HTTP) {
    options.push({ value: MetricSourceType.HTTP, text: 'Measured automatically' })
  }
  return options
})

const selectedMeasurement = computed(() => measurements.value.find((measurement) => measurement.id === form.measurementId))

onMounted(() => {
  Promise.resolve()
    .then(() => measurementsApi.list())
    .then((available) => {
      measurements.value = available ?? []
    })
    .catch(() => {
      measurements.value = []
    })
})

watch(selectedMeasurement, (measurement) => {
  if (measurement && !editing.value) {
    form.type = measurement.metricType
    form.unit = form.unit || measurement.unit || ''
  }
})

const hasDirection = computed(() => form.type === MetricType.NUMERIC || form.type === MetricType.SCALE)

function addChoice() {
  form.choices.push('')
}

function removeChoice(index) {
  form.choices.splice(index, 1)
}

function submit() {
  error.value = validateMetric(form)
  if (!error.value) {
    emit('save', buildMetricPayload(form))
  }
}
</script>

<template>
  <form class="metric-form flex flex-col gap-3" @submit.prevent="submit">
    <div class="flex flex-wrap gap-3">
      <va-input v-model="form.name" label="Name" placeholder="e.g. Resting heart rate" class="min-w-64 flex-1!" />
      <va-select
        v-model="form.type"
        class="metric-type"
        label="Type"
        :options="typeOptions"
        value-by="value"
        text-by="text"
        :disabled="editing || form.source === 'http'"
      />
    </div>
    <div class="flex flex-wrap gap-3">
      <va-select v-model="form.measures" label="Measures" :options="measuresOptions" value-by="value" text-by="text" />
      <va-select v-model="form.frequency" label="Frequency" :options="FrequencyType.ALL" />
      <va-select v-model="form.source" class="metric-source" label="Collected by" :options="sourceOptions" value-by="value" text-by="text" />
    </div>
    <div v-if="form.source === 'http'" class="measurement-settings flex flex-col gap-1">
      <va-select
        v-model="form.measurementId"
        class="measurement-id"
        label="Measure"
        :options="measurements"
        value-by="id"
        text-by="label"
      />
      <span class="text-xs text-slate-500">{{ selectedMeasurement?.description ?? 'Goals reads this value' }} once per {{ form.frequency === 'daily' ? 'day' : form.frequency === 'weekly' ? 'week' : 'month' }} and records it here.</span>
    </div>

    <div v-if="hasDirection" class="flex flex-wrap gap-3">
      <va-select v-model="form.direction" label="Direction" :options="MetricDirectionType.ALL" />
      <va-input v-if="form.type === 'numeric'" v-model="form.unit" label="Unit" placeholder="e.g. bpm" />
      <va-input v-model="form.baseline" label="Baseline" type="number" />
      <va-input v-model="form.target" label="Target" type="number" />
      <va-input v-if="form.direction === 'maintain'" v-model="form.tolerance" label="Tolerance (±)" type="number" />
    </div>

    <div v-if="form.type === 'scale'" class="flex flex-wrap gap-3">
      <va-input v-model="form.scaleMin" label="Lowest" type="number" :disabled="editing" />
      <va-input v-model="form.scaleMax" label="Highest" type="number" :disabled="editing" />
    </div>

    <va-input
      v-if="form.type === 'boolean'"
      v-model="form.targetPercent"
      class="target-percent"
      label="Target: % of answers that are Yes"
      type="number"
      placeholder="e.g. 85"
    />

    <div v-if="form.type === 'choice'" class="choices flex flex-col gap-2">
      <span class="text-sm text-slate-600">Choices, worst to best</span>
      <div v-for="(choice, index) in form.choices" :key="index" class="flex gap-2">
        <va-input v-model="form.choices[index]" class="choice-input flex-1!" :placeholder="`Choice ${index + 1}`" />
        <va-button v-if="form.choices.length > 2" preset="secondary" @click="removeChoice(index)">Remove</va-button>
      </div>
      <va-button preset="secondary" class="self-start" @click="addChoice">Add choice</va-button>
    </div>

    <va-textarea v-model="form.description" label="Description (optional)" autosize />
    <p v-if="error" class="form-error text-sm text-red-600">{{ error }}</p>
    <div class="flex gap-2">
      <va-button :loading="saving" @click="submit">{{ editing ? 'Save metric' : 'Add metric' }}</va-button>
      <va-button preset="secondary" @click="emit('cancel')">Cancel</va-button>
    </div>
  </form>
</template>
