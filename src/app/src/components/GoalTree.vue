<script setup>
import { computed, ref } from 'vue'
import StatusChip from './StatusChip.vue'

defineOptions({ name: 'GoalTree' })

const props = defineProps({
  node: { type: Object, required: true },
  selectedId: { type: String, default: null }
})

const emit = defineEmits(['select'])

const expanded = ref(true)
const children = computed(() => props.node.children ?? [])
</script>

<template>
  <div class="goal-tree">
    <div
      class="tree-row flex cursor-pointer items-center gap-2 rounded px-2 py-1"
      :class="{ 'bg-slate-100 font-semibold': node.goal.id === selectedId }"
      @click="emit('select', node.goal.id)"
    >
      <button
        v-if="children.length"
        type="button"
        class="tree-toggle w-4 text-slate-500"
        :aria-label="expanded ? 'Collapse' : 'Expand'"
        @click.stop="expanded = !expanded"
      >
        {{ expanded ? '▾' : '▸' }}
      </button>
      <span v-else class="w-4"></span>
      <span class="tree-title flex-1">{{ node.goal.title }}</span>
      <status-chip v-if="node.goal.status !== 'active'" :status="node.goal.status" />
    </div>
    <div v-if="expanded && children.length" class="ml-4 border-l border-slate-200 pl-1">
      <goal-tree
        v-for="child in children"
        :key="child.goal.id"
        :node="child"
        :selected-id="selectedId"
        @select="emit('select', $event)"
      />
    </div>
  </div>
</template>
