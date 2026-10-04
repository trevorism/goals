<script setup>
import { onMounted, reactive, ref } from 'vue'
import { delegatesApi, errorMessage } from '../api.js'
import { formatDay } from '../forms.js'

const delegates = ref([])
const error = ref('')
const saving = ref(false)
const form = reactive({ delegateId: '', label: '', access: 'edit' })
const accessOptions = [
  { value: 'edit', text: 'Read and edit (never delete)' },
  { value: 'read', text: 'Read only' }
]

async function load() {
  try {
    delegates.value = await delegatesApi.list()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

async function grant() {
  if (!form.delegateId.trim() || !form.label.trim()) {
    error.value = 'Enter the identity id and a name for it'
    return
  }
  saving.value = true
  error.value = ''
  try {
    await delegatesApi.grant({ delegateId: form.delegateId.trim(), label: form.label.trim(), access: form.access })
    Object.assign(form, { delegateId: '', label: '', access: 'edit' })
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    saving.value = false
  }
}

async function revoke(delegate) {
  if (!window.confirm(`Stop ${delegate.label} from acting on your goals?`)) {
    return
  }
  try {
    await delegatesApi.revoke(delegate.id)
    await load()
  } catch (e) {
    error.value = errorMessage(e)
  }
}

onMounted(load)
</script>

<template>
  <section class="settings flex flex-col gap-4">
    <h1 class="text-2xl font-semibold">Settings</h1>

    <va-card>
      <va-card-title>Who can act on your goals</va-card-title>
      <va-card-content class="flex flex-col gap-3">
        <p class="text-sm text-slate-600">
          Let another identity, such as an assistant, read and record your goals. It acts for you by adding
          <code>?onBehalfOf=&lt;your id&gt;</code> to its requests. Delegates can never delete, and anything they create shows who added it.
        </p>

        <ul v-if="delegates.length" class="flex flex-col gap-2 text-sm">
          <li v-for="delegate in delegates" :key="delegate.id" class="delegate flex flex-wrap items-center gap-2">
            <span class="delegate-label font-semibold">{{ delegate.label }}</span>
            <span class="text-slate-500">{{ delegate.delegateId }}</span>
            <va-chip size="small" outline color="secondary">{{ delegate.access === 'edit' ? 'read and edit' : 'read only' }}</va-chip>
            <span class="text-xs text-slate-500">since {{ formatDay(delegate.createdDate) }}</span>
            <va-button preset="plain" size="small" color="danger" @click="revoke(delegate)">Revoke</va-button>
          </li>
        </ul>
        <p v-else class="no-delegates text-sm text-slate-500">Only you can act on your goals.</p>

        <form class="grant-form flex flex-wrap items-end gap-2" @submit.prevent="grant">
          <va-input v-model="form.delegateId" class="delegate-id" label="Identity id" placeholder="e.g. 4856675479584768" />
          <va-input v-model="form.label" class="delegate-name" label="Name" placeholder="e.g. Claude" />
          <va-select v-model="form.access" label="Access" :options="accessOptions" value-by="value" text-by="text" />
          <va-button :loading="saving" @click="grant">Give access</va-button>
        </form>
        <p v-if="error" class="settings-error text-sm text-red-600">{{ error }}</p>
      </va-card-content>
    </va-card>
  </section>
</template>
