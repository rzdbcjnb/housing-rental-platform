import pluginVue from 'eslint-plugin-vue'
import configPrettier from 'eslint-config-prettier'

export default [
  {
    name: 'app/base',
    rules: {
      'no-unused-vars': 'warn',
      'no-console': 'warn'
    }
  },
  ...pluginVue.configs['flat/recommended'],
  configPrettier,
  {
    name: 'app/custom',
    rules: {
      'vue/multi-word-component-names': 'off'
    }
  }
]
