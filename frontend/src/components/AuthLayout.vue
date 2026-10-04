<script setup>
import { RouterLink } from 'vue-router'
import UiIcon from './UiIcon.vue'
</script>

<template>
  <!-- Login und Registrierung teilen sich diesen Rahmen; ihr jeweiliges Formular wird über den Slot eingesetzt. -->
  <main class="auth-layout">
    <section class="auth-story" aria-label="Team Workspace">
      <RouterLink :to="{ name: 'login' }" class="brand auth-brand">
        <span class="brand-mark"><UiIcon name="grid" /></span>
        <span>Team<span class="brand-secondary">Workspace</span></span>
      </RouterLink>
      <div class="auth-story-content">
        <h2>Projekte und Aufgaben gemeinsam verwalten.</h2>
        <p>Organisiere deine Teams, teile Aufgaben und behalte den aktuellen Stand im Blick.</p>
      </div>
    </section>
    <section class="auth-form-area"><div class="auth-form-wrap"><slot /></div></section>
  </main>
</template>
