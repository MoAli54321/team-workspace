<script setup>
import { computed } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { useAuthStore } from '../stores/auth.js'
import UiIcon from './UiIcon.vue'

// Die Seiten liefern ihren Navigationspfad und Inhalt; dieser Rahmen hält die Bedienung überall gleich.
defineProps({
  breadcrumbs: { type: Array, default: () => [] },
})

const route = useRoute()
const authStore = useAuthStore()
const initials = computed(() => (authStore.username || 'U').slice(0, 2).toUpperCase())
// Auf der Projektseite steht das Team im Pfad, auf der Aufgabenseite kommt es aus dem Navigationskontext.
const teamId = computed(() => route.params.teamId || route.query.teamId)
const projectTarget = computed(() => ({
  name: 'team-projects',
  params: { teamId: teamId.value },
  query: { teamName: route.query.teamName },
}))
</script>

<template>
  <div class="workspace-shell">
    <!-- Mit der Tastatur lässt sich die wiederkehrende Navigation direkt überspringen. -->
    <a class="skip-link" href="#workspace-content">Zum Inhalt springen</a>
    <aside class="sidebar">
      <RouterLink class="brand" :to="{ name: 'dashboard' }" aria-label="Team Workspace – Meine Teams">
        <span class="brand-mark"><UiIcon name="grid" /></span>
        <span>Team<span class="brand-secondary">Workspace</span></span>
      </RouterLink>

      <div class="sidebar-navigation">
        <p class="nav-caption">Arbeitsplatz</p>
        <nav class="side-nav" aria-label="Hauptnavigation">
          <RouterLink :to="{ name: 'dashboard' }" :class="{ active: route.name === 'dashboard' }"
            :aria-current="route.name === 'dashboard' ? 'page' : undefined">
            <UiIcon name="users" /><span>Meine Teams</span>
          </RouterLink>
          <RouterLink v-if="teamId" :to="projectTarget" :class="{ active: route.name === 'team-projects' && route.query.tab !== 'members' }"
            :aria-current="route.name === 'team-projects' && route.query.tab !== 'members' ? 'page' : undefined">
            <UiIcon name="folder" /><span>Projekte</span>
          </RouterLink>
          <RouterLink v-if="teamId" :to="{ ...projectTarget, query: { ...projectTarget.query, tab: 'members' } }"
            :class="{ active: route.name === 'team-projects' && route.query.tab === 'members' }"
            :aria-current="route.name === 'team-projects' && route.query.tab === 'members' ? 'page' : undefined">
            <UiIcon name="users" /><span>Mitglieder</span>
          </RouterLink>
          <RouterLink v-if="route.name === 'project-tasks'" :to="route.fullPath" class="active" aria-current="page">
            <UiIcon name="task" /><span>Aufgaben</span>
          </RouterLink>
        </nav>
      </div>

      <div class="sidebar-account">
        <span class="avatar">{{ initials }}</span>
        <div class="account-info"><strong>{{ authStore.username }}</strong><span>Dein Arbeitsplatz</span></div>
        <button type="button" class="icon-button logout-button" aria-label="Abmelden" title="Abmelden"
          @click="authStore.logout()"><UiIcon name="logout" /></button>
      </div>
    </aside>

    <div class="workspace-main">
      <header class="topbar">
        <nav class="breadcrumbs" aria-label="Seitennavigation">
          <RouterLink :to="{ name: 'dashboard' }" :aria-current="breadcrumbs.length ? undefined : 'page'">
            <UiIcon name="grid" /><span>Meine Teams</span>
          </RouterLink>
          <template v-for="(crumb, index) in breadcrumbs" :key="index">
            <UiIcon name="chevron" />
            <RouterLink v-if="crumb.to" :to="crumb.to">{{ crumb.label }}</RouterLink>
            <span v-else aria-current="page">{{ crumb.label }}</span>
          </template>
        </nav>
      </header>
      <main id="workspace-content" class="workspace-page" tabindex="-1"><slot /></main>
    </div>
  </div>
</template>
