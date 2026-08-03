<script setup>
import AuthForm from '../../components/AuthForm.vue'
</script>

<template>
  <div class="auth-page pc-auth-page">
    <AuthForm terminal="pc" :registration-roles="['USER', 'SELLER']" />
  </div>
</template>
