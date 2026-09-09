<script setup>
import { ref, onMounted } from 'vue'

const backendStatus = ref('Checking backend...')

onMounted(async () => {
  try {
    const response = await fetch('/api/health')

    if (!response.ok) {
      throw new Error('Backend request failed')
    }

    backendStatus.value = await response.text()
  } catch (error) {
    backendStatus.value = 'Backend is not available'
    console.error(error)
  }
})
</script>

<template>
  <main>
    <h1>Team Workspace</h1>

    <h2>Backend Status</h2>

    <p>{{ backendStatus }}</p>
  </main>
</template>

<style scoped>
main {
  max-width: 800px;
  margin: 80px auto;
  padding: 20px;
  font-family: Arial, sans-serif;
}

h1 {
  font-size: 2.5rem;
}

h2 {
  margin-top: 40px;
}
</style>