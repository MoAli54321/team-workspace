import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import DashboardView from '../views/DashboardView.vue'
import ProjectsView from '../views/ProjectsView.vue'
import ProjectTasksView from '../views/ProjectTasksView.vue'
import { authGuard } from './authGuard'

// Die URL bildet den Weg durch die Anwendung ab: Teams → Projekte → Aufgaben.
// requiresAuth markiert Seiten, die der gemeinsame Guard nur mit gültiger Sitzung öffnet.
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: { name: 'dashboard' },
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      component: DashboardView,
      meta: { requiresAuth: true },
    },
    {
      path: '/teams/:teamId/projects',
      name: 'team-projects',
      component: ProjectsView,
      meta: { requiresAuth: true },
    },
    {
      path: '/projects/:projectId/tasks',
      name: 'project-tasks',
      component: ProjectTasksView,
      meta: { requiresAuth: true },
    },
  ],
})

// Ein zentraler Guard hält die Anmelderegeln für alle geschützten Seiten zusammen.
router.beforeEach(authGuard)

export default router
