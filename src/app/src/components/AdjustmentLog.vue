<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { adjustmentsApi, errorMessage } from '../api.js'
import { formatDay, toDay } from '../forms.js'
import { AdjustmentCategoryType } from '../types.js'

const props = defineProps({
  goalId: { type: String, required: true },
  metrics: { type: Array, default: () => [] }
})

const emit = defineEmits(['changed'])

const adjustments = ref([])
const adding = ref(false)
const error = ref('')

const blankForm = () => ({ title: '', description: '', category: AdjustmentCategoryType.HABIT, effectiveDate: new Date(), metricIds: [] })
const form = reactive(blankForm())

async function load() {
  try {
    adjustments.value = await adjustmentsApi.list(props.goalId)
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function save() {
  if (!form.title.trim()) {
    error.value = 'Describe the change'
    return
  }
  error.value = ''
  try {
    await adjustmentsApi.create(props.goalId, {
      title: form.title.trim(),
      description: form.description.trim() || null,
      category: form.category,
      effectiveDate: toDay(form.effectiveDate),
      metricIds: form.metricIds
    })
    Object.assign(form, blankForm())
    adding.value = false
    await load()
    emit('changed')
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function remove(adjustment) {
  if (!window.confirm(`Delete "${adjustment.title}"?`)) {
    return
  }
  try {
    await adjustmentsApi.remove(adjustment.id)
    await load()
    emit('changed')
  } catch (e) {
    error.value = errorMessage(e)
  }
}

watch(() => props.goalId, () => {
  adding.value = false
  load()
})

onMounted(load)
</script>

<template>
  <section class="adjustment-log flex flex-col gap-2">
    <div class="flex items-center justify-between">
      <h3 class="text-lg font-semibold">Adjustments</h3>
      <va-button v-if="!adding" preset="secondary" size="small" @click="adding = true">Add adjustment</va-button>
    </div>
    <p class="text-sm text-slate-500">Behavior changes you made, so you can see what moved the numbers.</p>

    <form v-if="adding" class="adjustment-form flex flex-col gap-2" @submit.prevent="save">
      <va-input v-model="form.title" class="adjustment-title" label="What changed?" placeholder="e.g. Started walking after lunch" />
      <div class="flex flex-wrap gap-2">
        <va-select v-model="form.category" label="Category" :options="AdjustmentCategoryType.ALL" />
        <va-date-input v-model="form.effectiveDate" label="Starting" />
        <va-select
          v-if="metrics.length"
          v-model="form.metricIds"
          label="Affects (optional)"
          :options="metrics"
          value-by="id"
          text-by="name"
          multiple
        />
      </div>
      <va-textarea v-model="form.description" label="Details (optional)" autosize />
      <div class="flex gap-2">
        <va-button size="small" @click="save">Save adjustment</va-button>
        <va-button preset="secondary" size="small" @click="adding = false">Cancel</va-button>
      </div>
    </form>

    <p v-if="error" class="log-error text-sm text-red-600">{{ error }}</p>
    <ul v-if="adjustments.length" class="flex flex-col gap-1 text-sm">
      <li v-for="adjustment in adjustments" :key="adjustment.id" class="adjustment flex items-center gap-2">
        <span class="w-28 text-slate-500">{{ formatDay(adjustment.effectiveDate) }}</span>
        <va-chip size="small" outline color="secondary">{{ adjustment.category }}</va-chip>
        <span class="adjustment-text flex-1">{{ adjustment.title }}</span>
        <va-button preset="plain" size="small" color="danger" @click="remove(adjustment)">Delete</va-button>
      </li>
    </ul>
    <p v-else-if="!adding" class="no-adjustments text-sm text-slate-500">No adjustments recorded.</p>
  </section>
</template>
