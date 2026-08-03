<script setup>
import AuthForm from '../../components/AuthForm.vue'
</script>

<template>
  <div class="auth-page mobile-auth-page">
    <AuthForm terminal="mobile" :registration-roles="['USER', 'SELLER']" />
  </div>
</template>
