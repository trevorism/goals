<script setup>
import { computed } from 'vue'
import { chartColors, formatPercent } from '../progress.js'

const props = defineProps({
  label: { type: String, required: true },
  value: { type: Number, default: null },
  expected: { type: Number, default: null }
})

const percent = (fraction) => `${Math.max(0, Math.min(1, fraction)) * 100}%`
const fillWidth = computed(() => percent(props.value ?? 0))
const markerLeft = computed(() => percent(props.expected ?? 0))
</script>

<template>
  <div class="progress-meter flex flex-col gap-1">
    <div class="flex items-baseline justify-between text-sm">
      <span class="meter-label text-slate-600">{{ label }}</span>
      <span class="meter-value font-semibold text-slate-900">{{ formatPercent(value) }}</span>
    </div>
    <div
      class="relative h-2 rounded-full"
      :style="{ backgroundColor: chartColors.meterTrack }"
      role="meter"
      :aria-label="label"
      aria-valuemin="0"
      aria-valuemax="100"
      :aria-valuenow="value === null ? undefined : Math.round(value * 100)"
    >
      <div class="meter-fill h-2 rounded-full" :style="{ width: fillWidth, backgroundColor: chartColors.meterFill }"></div>
      <div
        v-if="expected !== null"
        class="meter-marker absolute -top-1 h-4 w-0.5"
        :style="{ left: markerLeft, backgroundColor: chartColors.secondaryInk }"
        :title="`Plan: ${formatPercent(expected)}`"
      ></div>
    </div>
  </div>
</template>
