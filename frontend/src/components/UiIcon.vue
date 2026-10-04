<script setup>
defineProps({ name: { type: String, required: true } })

// Alle Symbole teilen dieselbe Größe und Strichstärke. Die Namen halten die Aufrufe in den Seiten lesbar.
const paths = {
  grid: 'M3 3h7v7H3z M14 3h7v7h-7z M3 14h7v7H3z M14 14h7v7h-7z',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M16 3a4 4 0 0 1 0 8 M22 21v-2a4 4 0 0 0-3-3.87 M13 7a4 4 0 1 1-8 0a4 4 0 0 1 8 0',
  folder: 'M3 7V5a2 2 0 0 1 2-2h5l2 3h7a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z',
  task: 'M9 5h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V9 M3 4l2 2 4-4 M8 12h8 M8 16h5',
  arrow: 'M5 12h14 M13 6l6 6-6 6',
  chevron: 'M9 5l7 7-7 7',
  plus: 'M12 5v14 M5 12h14',
  refresh: 'M20 7a9 9 0 1 0 1 8 M20 2v5h-5',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4 M16 17l5-5-5-5 M21 12H9',
  check: 'M5 12l4 4L19 6',
  clock: 'M12 8v4l3 2 M21 12a9 9 0 1 1-18 0a9 9 0 0 1 18 0',
  edit: 'M16 3l5 5 M4 15 16 3a3.5 3.5 0 0 1 5 5L9 20l-6 1 1-6Z',
  trash: 'M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
  eye: 'M2 12s3.5-6 10-6 10 6 10 6-3.5 6-10 6S2 12 2 12 M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0',
  eyeOff: 'M3 3l18 18 M10.6 6.2A11 11 0 0 1 12 6c6.5 0 10 6 10 6a16 16 0 0 1-2.3 3.1 M6.2 6.2C3.5 8 2 12 2 12s3.5 6 10 6c1.5 0 2.8-.3 4-.8 M9.9 9.9a3 3 0 0 0 4.2 4.2',
}
</script>

<template>
  <!-- Das Symbol ist dekorativ. Die Bedeutung liefern der sichtbare Text oder das aria-label am Bedienelement. -->
  <svg class="ui-icon" width="20" height="20" viewBox="0 0 24 24" fill="none"
    stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"
    aria-hidden="true" focusable="false">
    <path :d="paths[name] || paths.grid" />
  </svg>
</template>
