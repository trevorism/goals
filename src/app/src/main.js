import { createApp } from 'vue'
import App from './App.vue'

import { TrevorismAuth } from '@trevorism/ui-auth'
import VueClickAway from 'vue3-click-away'
import { createVuestic } from 'vuestic-ui'
import config from '../vuestic.config.js'
import './style.css'

const app = createApp(App)
app.use(TrevorismAuth)
app.use(VueClickAway)
app.use(createVuestic({ config }))
app.mount('#app')
