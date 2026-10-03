<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: { type: String, required: true }
})

const colors = {
  active: 'info',
  completed: 'success',
  missed: 'warning',
  abandoned: 'secondary'
}

const color = computed(() => colors[props.status] ?? 'secondary')
</script>

<template>
  <va-chip class="status-chip" size="small" outline :color="color">{{ status }}</va-chip>
</template>
