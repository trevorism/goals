<script setup>
import { reactive, ref } from 'vue'
import { buildObservationPayload, scalePoints, validateObservation } from '../forms.js'

const props = defineProps({
  metric: { type: Object, required: true },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['record'])

const blankEntry = () => ({ value: '', choice: null, text: '', note: '', observedAt: new Date() })
const entry = reactive(blankEntry())
const error = ref('')

function submit() {
  error.value = validateObservation(props.metric, entry)
  if (!error.value) {
    emit('record', buildObservationPayload(props.metric, entry))
    Object.assign(entry, blankEntry())
  }
}
</script>

<template>
  <form class="observation-entry flex flex-col gap-2" @submit.prevent="submit">
    <div class="flex flex-wrap items-end gap-2">
      <va-input
        v-if="metric.type === 'numeric'"
        v-model="entry.value"
        class="value-input"
        type="number"
        :label="metric.unit ? `Value (${metric.unit})` : 'Value'"
      />

      <div v-if="metric.type === 'scale'" class="scale-points flex gap-1">
        <va-button
          v-for="point in scalePoints(metric)"
          :key="point"
          class="point-button"
          size="small"
          :preset="Number(entry.value) === point && entry.value !== '' ? 'primary' : 'secondary'"
          border-color="primary"
          @click="entry.value = point"
        >
          {{ point }}
        </va-button>
      </div>

      <div v-if="metric.type === 'boolean' || metric.type === 'choice'" class="choice-buttons flex flex-wrap gap-1">
        <va-button
          v-for="choice in metric.choices"
          :key="choice.value"
          class="choice-button"
          size="small"
          :preset="entry.choice === choice.value ? 'primary' : 'secondary'"
          border-color="primary"
          @click="entry.choice = choice.value"
        >
          {{ choice.label }}
        </va-button>
      </div>

      <va-textarea v-if="metric.type === 'text'" v-model="entry.text" class="text-input min-w-64 flex-1!" label="Entry" autosize />

      <va-date-input v-model="entry.observedAt" class="observed-at w-40!" label="Date" />
      <va-input v-model="entry.note" class="note-input min-w-48 flex-1!" label="Note (optional)" />
      <va-button class="record-button" :loading="saving" @click="submit">Record</va-button>
    </div>
    <p v-if="error" class="form-error text-sm text-red-600">{{ error }}</p>
  </form>
</template>
