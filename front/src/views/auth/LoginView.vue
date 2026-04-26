<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const submitting = ref(false)
const errorMessage = ref('')

const form = reactive({
  username: '',
  password: ''
})

async function onSubmit() {
  if (!form.username) {
    errorMessage.value = '请输入用户名。'
    return
  }
  if (!form.password) {
    errorMessage.value = '请输入密码。'
    return
  }
  submitting.value = true
  errorMessage.value = ''
  try {
    await authStore.login(form)
    router.push(route.query.redirect || authStore.getDefaultRoute())
  } catch (error) {
    errorMessage.value = error.message
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="pg">
    <header class="nav">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">C</span>
        <span class="brand-name">校园论坛</span>
      </RouterLink>
      <nav class="nav-actions">
        <RouterLink class="link-ghost" to="/register">注册</RouterLink>
      </nav>
    </header>
    <section class="center">
      <div class="blob blob-1" aria-hidden="true"></div>
      <div class="blob blob-2" aria-hidden="true"></div>
      <div class="shape shape-a" aria-hidden="true"></div>
      <div class="shape shape-b" aria-hidden="true"></div>
      <div class="shape shape-c" aria-hidden="true"></div>

      <form class="card" @submit.prevent="onSubmit">
        <h1>登录</h1>
        <p class="sub">欢迎回来</p>

        <label class="field">
          <span class="label">用户名</span>
          <input v-model.trim="form.username" type="text" placeholder="请输入用户名" />
        </label>
        <label class="field">
          <span class="label">密码</span>
          <input v-model="form.password" type="password" placeholder="请输入密码" />
        </label>

        <p v-if="errorMessage" class="err">{{ errorMessage }}</p>

        <button class="btn-primary" type="submit" :disabled="submitting">
          <span v-if="!submitting">登录系统</span>
          <span v-else>登录中...</span>
        </button>

        <p class="footnote">还没有账号？<RouterLink to="/register">立即注册</RouterLink></p>
      </form>
    </section>
  </main>
</template>

<style scoped>
.pg {
  --c-bg: oklch(0.975 0.006 280);
  --c-surface: oklch(0.995 0.002 280);
  --c-text: oklch(0.16 0.025 280);
  --c-sub: oklch(0.44 0.02 280);
  --c-primary: oklch(0.50 0.22 285);
  --c-accent: oklch(0.64 0.2 22);
  --c-accent-soft: oklch(0.93 0.04 22);
  --c-teal: oklch(0.68 0.14 178);
  --c-teal-soft: oklch(0.93 0.035 178);
  --c-border: oklch(0.89 0.01 280);
  min-height: 100vh;
  background: var(--c-bg);
  background-image: radial-gradient(oklch(0.86 0.008 280) 1px, transparent 1px);
  background-size: 28px 28px;
  color: var(--c-text);
  overflow-x: hidden;
}
/* --- nav (same as SiteHomeView) --- */
.nav {
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  padding: 20px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  z-index: 2;
}
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: var(--c-text);
}
.brand-mark {
  width: 38px; height: 38px;
  display: grid; place-items: center;
  border-radius: 10px;
  background: var(--c-accent);
  color: oklch(1 0 0);
  font-weight: 800; font-size: 18px;
  transition: transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.brand:hover .brand-mark { transform: rotate(-8deg) scale(1.08); }
.brand-name { font-weight: 800; font-size: 17px; letter-spacing: -0.01em; }
.nav-actions { display: flex; gap: 8px; }

.link-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
  padding: 0 20px;
  border-radius: 10px;
  font-weight: 700;
  font-size: 14px;
  text-decoration: none;
  background: var(--c-surface);
  color: var(--c-primary);
  border: 1.5px solid var(--c-border);
  transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1),
              box-shadow 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}
.link-ghost:hover {
  border-color: var(--c-primary);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px oklch(0.50 0.22 285 / 0.12);
}
/* --- center section --- */
.center {
  position: relative;
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  display: grid;
  place-items: center;
  min-height: calc(100vh - 100px);
}

/* blobs + shapes (same as SiteHomeView) */
.blob { position: absolute; border-radius: 50%; pointer-events: none; z-index: 0; }
.blob-1 {
  width: 380px; height: 380px;
  top: -100px; right: -80px;
  background: var(--c-accent-soft); opacity: 0.6;
  filter: blur(70px); animation: blobDrift1 12s ease-in-out infinite;
}
.blob-2 {
  width: 280px; height: 280px;
  bottom: -60px; left: -100px;
  background: var(--c-teal-soft); opacity: 0.55;
  filter: blur(55px); animation: blobDrift2 14s ease-in-out infinite;
}
.shape { position: absolute; pointer-events: none; z-index: 0; }
.shape-a {
  width: 12px; height: 12px; border-radius: 50%;
  background: var(--c-accent); opacity: 0.5;
  top: 18%; left: 8%;
  animation: shapeA 7s ease-in-out infinite;
}
.shape-b {
  width: 10px; height: 10px; border-radius: 3px;
  background: var(--c-primary); opacity: 0.4;
  top: 50%; right: 10%;
  animation: shapeB 9s ease-in-out infinite;
}
.shape-c {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--c-teal); opacity: 0.5;
  bottom: 25%; left: 30%;
  animation: shapeC 11s ease-in-out infinite;
}

/* --- form card --- */
.card {
  position: relative; z-index: 1;
  width: 100%; max-width: 400px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  border-radius: 16px;
  padding: 36px 32px;
  display: grid; gap: 16px;
  box-shadow: 0 24px 60px oklch(0.16 0.025 280 / 0.1),
              0 0 0 1px oklch(0.16 0.025 280 / 0.03);
  animation: cardIn 0.6s cubic-bezier(0.22, 1, 0.36, 1) 0.1s both;
}
.card h1 { margin: 0; font-size: 26px; font-weight: 900; letter-spacing: -0.02em; }
.sub { margin: -8px 0 4px; color: var(--c-sub); font-size: 14px; }

.field { display: grid; gap: 6px; }
.label { font-size: 13px; font-weight: 600; color: var(--c-sub); }
.field input {
  width: 100%; padding: 11px 14px;
  border: 1.5px solid var(--c-border);
  border-radius: 10px; font-size: 14px;
  background: var(--c-bg);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}
.field input:focus {
  outline: none;
  border-color: var(--c-primary);
  box-shadow: 0 0 0 3px oklch(0.50 0.22 285 / 0.1);
}
.field input::placeholder { color: oklch(0.7 0.01 280); }

.btn-primary {
  width: 100%; padding: 12px;
  border: none; border-radius: 10px;
  background: var(--c-primary);
  color: oklch(1 0 0);
  font-size: 15px; font-weight: 700; cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1),
              box-shadow 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}
.btn-primary:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px oklch(0.50 0.22 285 / 0.35);
}
.btn-primary:active:not(:disabled) { transform: translateY(0); }
.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }

.err {
  margin: 0; padding: 10px 12px; border-radius: 10px;
  background: oklch(0.94 0.04 25); color: oklch(0.45 0.16 25); font-size: 13px;
}
.footnote { margin: 0; text-align: center; color: var(--c-sub); font-size: 13px; }
.footnote a { color: var(--c-primary); font-weight: 700; text-decoration: none; }
.footnote a:hover { text-decoration: underline; }

/* --- keyframes (same as SiteHomeView) --- */
@keyframes cardIn {
  from { opacity: 0; transform: translateY(24px); }
  to { opacity: 1; transform: none; }
}
@keyframes blobDrift1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-30px, 20px) scale(1.08); }
}
@keyframes blobDrift2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(25px, -15px) scale(1.05); }
}
@keyframes shapeA {
  0%, 100% { transform: translate(0, 0); }
  33% { transform: translate(18px, -22px); }
  66% { transform: translate(-10px, 12px); }
}
@keyframes shapeB {
  0%, 100% { transform: translate(0, 0) rotate(0deg); }
  50% { transform: translate(-14px, 18px) rotate(90deg); }
}
@keyframes shapeC {
  0%, 100% { transform: translate(0, 0); }
  40% { transform: translate(20px, -10px); }
  80% { transform: translate(-12px, 16px); }
}

@media (max-width: 520px) {
  .nav { flex-direction: column; align-items: flex-start; }
  .card { padding: 28px 20px; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
