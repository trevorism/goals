const field = (tag, className) => ({
  props: ['modelValue', 'label', 'disabled'],
  emits: ['update:modelValue'],
  template: `<${tag} class="${className}" :data-label="label" :disabled="disabled" :value="modelValue" @input="$emit('update:modelValue', $event.target.value)"></${tag}>`
})

export const stubs = {
  'va-input': field('input', 'va-input'),
  'va-textarea': field('textarea', 'va-textarea'),
  'va-select': {
    props: ['modelValue', 'label', 'options', 'disabled'],
    emits: ['update:modelValue'],
    template: '<div class="va-select" :data-label="label" :data-disabled="disabled" :data-value="JSON.stringify(modelValue)"></div>'
  },
  'va-date-input': { props: ['modelValue', 'label'], template: '<div class="va-date-input" :data-label="label"></div>' },
  'va-button': {
    props: ['preset', 'loading'],
    emits: ['click'],
    template: '<button class="va-button" :data-preset="preset" @click="$emit(\'click\')"><slot /></button>'
  },
  'va-card': { template: '<div class="va-card"><slot /></div>' },
  'va-card-title': { template: '<div class="va-card-title"><slot /></div>' },
  'va-card-content': { template: '<div class="va-card-content"><slot /></div>' },
  'va-chip': { template: '<span class="va-chip"><slot /></span>' },
  'router-link': { props: ['to'], template: '<a class="router-link"><slot /></a>' }
}

export function button(wrapper, label) {
  return wrapper.findAll('.va-button').find((element) => element.text().trim() === label)
}

export function labelled(wrapper, selector, label) {
  return wrapper.findAll(selector).find((element) => element.attributes('data-label') === label)
}
