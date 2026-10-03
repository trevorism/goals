<script setup>
import { computed } from 'vue'
import { statusDisplay } from '../progress.js'

const props = defineProps({
  status: { type: String, default: 'no_data' },
  compact: { type: Boolean, default: false }
})

const display = computed(() => statusDisplay[props.status] ?? statusDisplay.no_data)
</script>

<template>
  <span class="progress-status inline-flex items-center gap-1 text-sm text-slate-700" :title="display.label">
    <span class="status-icon inline-flex h-4 w-4 items-center justify-center rounded-full text-[10px] font-bold text-white" :style="{ backgroundColor: display.color }" aria-hidden="true">{{ display.icon }}</span>
    <span :class="compact ? 'sr-only' : 'status-label'">{{ display.label }}</span>
  </span>
</template>
