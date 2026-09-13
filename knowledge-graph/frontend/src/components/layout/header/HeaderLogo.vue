<template>
  <router-link to="/" class="xl:hidden">
    <AppBrandLogo />
  </router-link>
</template>

<script setup lang="ts">
import { RouterLink } from 'vue-router'
import AppBrandLogo from '../AppBrandLogo.vue'
</script>
