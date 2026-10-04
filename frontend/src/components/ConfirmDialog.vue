<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import UiIcon from './UiIcon.vue'

// Gemeinsame Rückfrage für Löschaktionen und das Entfernen von Mitgliedern.
// Er meldet die Entscheidung an die Seite zurück und führt selbst keine API-Anfrage aus.
const props = defineProps({
  title: { type: String, required: true },
  message: { type: String, required: true },
  confirmLabel: { type: String, default: 'Löschen' },
  busy: { type: Boolean, default: false },
  error: { type: String, default: '' },
})
const emit = defineEmits(['confirm', 'cancel'])
const dialog = ref(null)
const cancelButton = ref(null)

// Ein laufender Löschvorgang lässt sich durch Schließen des Dialogs nicht rückgängig machen.
function cancel() {
  if (!props.busy) emit('cancel')
}

onMounted(() => {
  // Der native Dialog hält den Tastaturfokus im Fenster; die erste Auswahl ist bewusst „Abbrechen“.
  dialog.value.showModal()
  cancelButton.value.focus()
})
onBeforeUnmount(() => dialog.value?.close())
</script>

<template>
  <!-- Außerhalb des Seitenlayouts bleibt der Dialog unabhängig von dessen Abständen und Begrenzungen. -->
  <Teleport to="body">
    <dialog ref="dialog" class="confirm-dialog" aria-labelledby="confirm-title" aria-describedby="confirm-message"
      :aria-busy="busy" @cancel.prevent="cancel">
      <span class="dialog-icon"><UiIcon name="trash" /></span>
      <h2 id="confirm-title">{{ title }}</h2>
      <p id="confirm-message">{{ message }}</p>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div class="dialog-actions">
        <button ref="cancelButton" type="button" class="button button-secondary" :disabled="busy" @click="cancel">Abbrechen</button>
        <button type="button" class="button button-danger" :disabled="busy" @click="emit('confirm')">{{ busy ? 'Bitte warten …' : confirmLabel }}</button>
      </div>
    </dialog>
  </Teleport>
</template>
