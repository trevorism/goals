<script setup>
import { computed, reactive, ref } from 'vue'
import { buildMetricPayload, metricFormFrom, validateMetric } from '../forms.js'
import { FrequencyType, MetricDirectionType, MetricMeasuresType, MetricType, isEachValueTarget, metricDirectionLabels, metricTypeLabels } from '../types.js'

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

const hasDirection = computed(() => form.type === MetricType.NUMERIC || form.type === MetricType.SCALE)
const directionOptions = MetricDirectionType.ALL.map((value) => ({ value, text: metricDirectionLabels[value] }))
const countsTimesPerWeek = computed(() => form.type === MetricType.BOOLEAN && form.frequency === 'daily' && Number(form.timesPerWeek) > 0)

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
        :disabled="editing"
      />
    </div>
    <div class="flex flex-wrap gap-3">
      <va-select v-model="form.measures" label="Measures" :options="measuresOptions" value-by="value" text-by="text" />
      <va-select v-model="form.frequency" label="Frequency" :options="FrequencyType.ALL" />
      <va-switch v-if="editing" v-model="form.enabled" class="metric-enabled" label="Active" />
    </div>
    <span v-if="editing && !form.enabled" class="paused-hint text-xs text-slate-500">Paused: hidden from Today and left out of progress until you turn it back on.</span>

    <div v-if="hasDirection" class="flex flex-wrap gap-3">
      <va-select v-model="form.direction" class="metric-direction" label="Direction" :options="directionOptions" value-by="value" text-by="text" />
      <va-input v-if="form.type === 'numeric'" v-model="form.unit" label="Unit" placeholder="e.g. bpm" />
      <va-input v-if="!isEachValueTarget(form)" v-model="form.baseline" label="Baseline" type="number" />
      <va-input v-model="form.target" label="Target" type="number" />
      <va-input v-if="form.direction === 'maintain'" v-model="form.tolerance" label="Tolerance (±)" type="number" />
    </div>

    <div v-if="form.type === 'scale'" class="flex flex-wrap gap-3">
      <va-input v-model="form.scaleMin" label="Lowest" type="number" :disabled="editing" />
      <va-input v-model="form.scaleMax" label="Highest" type="number" :disabled="editing" />
    </div>

    <va-input
      v-if="form.type === 'boolean' && form.frequency === 'daily'"
      v-model="form.timesPerWeek"
      class="times-per-week"
      label="Times per week (optional)"
      type="number"
      placeholder="e.g. 3"
    />
    <va-input
      v-if="form.type === 'boolean' && !countsTimesPerWeek"
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
