<script>
import axios from 'axios'

export default {
  data() {
    return {
      count: 0,
      val: ''
    }
  },
  mounted() {
    axios
      .get('api/ping')
      .then((x) => {
        this.val = x.data
      })
      .catch(() => {
        this.val = 'Error'
      })
  }
}
</script>

<script setup>
import { VaIcon } from 'vuestic-ui'

defineProps({
  msg: String
})
</script>

<template>
  <div class="grid justify-items-center">
    <h1>{{ msg }}</h1>
    <h1>{{ val }}</h1>
    <va-icon name="home" size="large" color="red-400"></va-icon>
  </div>
</template>

<style scoped></style>
