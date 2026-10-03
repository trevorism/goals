<script setup>
import { computed } from 'vue'
import { Chart as ChartJS, LinearScale, PointElement, LineElement, Tooltip, Legend } from 'chart.js'
import annotationPlugin from 'chartjs-plugin-annotation'
import { Scatter } from 'vue-chartjs'
import { buildMetricChart } from '../progress.js'

ChartJS.register(LinearScale, PointElement, LineElement, Tooltip, Legend, annotationPlugin)

const props = defineProps({
  metric: { type: Object, required: true },
  goal: { type: Object, required: true },
  observations: { type: Array, default: () => [] },
  metricProgress: { type: Object, default: null },
  now: { type: [Date, String], default: () => new Date() },
  segments: { type: Array, default: () => [] }
})

const chart = computed(() => buildMetricChart(props.metric, props.goal, props.observations, props.metricProgress, new Date(props.now), props.segments))
</script>

<template>
  <div v-if="chart && observations.length" class="metric-chart h-56">
    <scatter :data="chart.data" :options="chart.options" />
  </div>
</template>
