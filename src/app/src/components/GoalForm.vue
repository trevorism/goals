<script setup>
import { reactive, ref } from 'vue'
import { buildGoalPayload, fromDay, validateGoal } from '../forms.js'
import { GoalStatusType } from '../types.js'

const props = defineProps({
  goal: { type: Object, default: null },
  requireDates: { type: Boolean, default: false },
  showStatus: { type: Boolean, default: false },
  submitLabel: { type: String, default: 'Save' },
  saving: { type: Boolean, default: false }
})

const emit = defineEmits(['save', 'cancel'])

const form = reactive({
  title: props.goal?.title ?? '',
  description: props.goal?.description ?? '',
  status: props.goal?.status ?? GoalStatusType.ACTIVE,
  startDate: fromDay(props.goal?.startDate),
  endDate: fromDay(props.goal?.endDate),
  definitionOfDone: props.goal?.definitionOfDone ?? ''
})

const error = ref('')

function submit() {
  error.value = validateGoal(form, { requireDates: props.requireDates })
  if (!error.value) {
    emit('save', buildGoalPayload(form))
  }
}
</script>

<template>
  <form class="goal-form flex flex-col gap-3" @submit.prevent="submit">
    <va-input v-model="form.title" label="Title" placeholder="What do you want to achieve?" />
    <va-textarea v-model="form.description" label="Description" autosize />
    <div class="flex flex-wrap gap-3">
      <va-date-input v-model="form.startDate" label="Start" :clearable="!requireDates" />
      <va-date-input v-model="form.endDate" label="End" :clearable="!requireDates" />
      <va-select v-if="showStatus" v-model="form.status" label="Status" :options="GoalStatusType.ALL" />
    </div>
    <va-textarea v-model="form.definitionOfDone" label="Definition of done (optional)" autosize />
    <p v-if="error" class="form-error text-sm text-red-600">{{ error }}</p>
    <div class="flex gap-2">
      <va-button :loading="saving" @click="submit">{{ submitLabel }}</va-button>
      <va-button preset="secondary" @click="emit('cancel')">Cancel</va-button>
    </div>
  </form>
</template>
