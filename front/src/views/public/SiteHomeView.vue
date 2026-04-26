<script setup>
import { computed, ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const entryPath = computed(() => authStore.getDefaultRoute())
const entered = ref(false)
onMounted(() => { requestAnimationFrame(() => { entered.value = true }) })

const pills = [
  { num: '01', label: '内容发布' },
  { num: '02', label: '审核治理' },
  { num: '03', label: '互动交流' }
]
</script>

<template>
  <main class="home" :class="{ entered }">
    <header class="nav">
      <RouterLink class="brand" to="/">
        <span class="brand-mark">C</span>
        <span class="brand-name">校园论坛</span>
      </RouterLink>
      <nav class="nav-actions">
        <RouterLink v-if="!authStore.isLoggedIn" class="link-ghost" to="/login">登录</RouterLink>
        <RouterLink v-if="!authStore.isLoggedIn" class="link-solid" to="/register">注册</RouterLink>
        <RouterLink v-else class="link-solid" :to="entryPath">进入系统</RouterLink>
      </nav>
    </header>

    <section class="hero">
      <div class="blob blob-1" aria-hidden="true"></div>
      <div class="blob blob-2" aria-hidden="true"></div>
      <div class="watermark" aria-hidden="true">C</div>

      <div class="hero-content">
        <span class="kicker">Campus Forum</span>
        <h1>
          <span class="line">校园里的一切</span><br>
          <span class="line line-accent">从这里开始</span>
        </h1>
        <p class="subtitle">通知、经验、讨论 — 一个平台搞定</p>
        <div class="hero-cta">
          <RouterLink v-if="!authStore.isLoggedIn" class="link-solid lg" to="/login">登录系统</RouterLink>
          <RouterLink v-if="!authStore.isLoggedIn" class="link-ghost lg" to="/register">注册账号</RouterLink>
          <RouterLink v-else class="link-solid lg" :to="entryPath">进入工作台</RouterLink>
        </div>
      </div>

      <div class="hero-aside">
        <div
          v-for="(p, i) in pills"
          :key="p.num"
          class="pill"
          :class="'pill-' + (i + 1)"
        >
          <span class="pill-num">{{ p.num }}</span>
          <span class="pill-label">{{ p.label }}</span>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.home {
  --c-bg: oklch(0.978 0.005 70);
  --c-surface: oklch(0.995 0.003 70);
  --c-text: oklch(0.15 0.02 50);
  --c-sub: oklch(0.45 0.015 50);
  --c-primary: oklch(0.50 0.22 285);
  --c-accent: oklch(0.62 0.2 25);
  --c-accent-soft: oklch(0.94 0.035 25);
  --c-teal: oklch(0.65 0.13 178);
  --c-teal-soft: oklch(0.94 0.03 178);
  --c-border: oklch(0.90 0.008 70);
  min-height: 100vh;
  background: var(--c-bg);
  color: var(--c-text);
  overflow-x: hidden;
}
/* --- nav --- */
.nav {
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  padding: 22px 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  position: relative;
  z-index: 2;
}
.brand {
  display: inline-flex; align-items: center;
  gap: 10px; text-decoration: none; color: var(--c-text);
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

/* --- shared link styles --- */
.link-solid,
.link-ghost {
  display: inline-flex; align-items: center; justify-content: center;
  min-height: 40px; padding: 0 20px;
  border-radius: 10px; font-weight: 700; font-size: 14px;
  text-decoration: none;
  transition: transform 0.25s cubic-bezier(0.22, 1, 0.36, 1),
              box-shadow 0.25s cubic-bezier(0.22, 1, 0.36, 1);
}
.link-solid {
  background: var(--c-primary); color: oklch(1 0 0); border: none;
}
.link-solid:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px oklch(0.50 0.22 285 / 0.3);
}
.link-ghost {
  background: var(--c-surface); color: var(--c-primary);
  border: 1.5px solid var(--c-border);
}
.link-ghost:hover {
  border-color: var(--c-primary); transform: translateY(-2px);
  box-shadow: 0 4px 12px oklch(0.50 0.22 285 / 0.1);
}
.lg { min-height: 48px; padding: 0 28px; font-size: 15px; border-radius: 12px; }
/* --- hero --- */
.hero {
  position: relative;
  width: min(1100px, calc(100% - 40px));
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  align-items: center;
  gap: 32px;
  min-height: calc(100vh - 90px);
}

/* decorative */
.blob { position: absolute; border-radius: 50%; pointer-events: none; z-index: 0; }
.blob-1 {
  width: 400px; height: 400px;
  top: -10%; right: -5%;
  background: var(--c-accent-soft); opacity: 0.55;
  filter: blur(80px);
  animation: drift1 14s ease-in-out infinite;
}
.blob-2 {
  width: 300px; height: 300px;
  bottom: -8%; left: -8%;
  background: var(--c-teal-soft); opacity: 0.45;
  filter: blur(65px);
  animation: drift2 16s ease-in-out infinite;
}
.watermark {
  position: absolute;
  right: 0; top: 50%;
  transform: translateY(-50%);
  font-size: clamp(220px, 28vw, 400px);
  font-weight: 900;
  color: var(--c-primary);
  opacity: 0.05;
  pointer-events: none;
  z-index: 0;
  line-height: 0.8;
  user-select: none;
}

/* --- hero content (left) --- */
.hero-content { position: relative; z-index: 1; }

.kicker {
  display: inline-block;
  padding: 5px 14px;
  border-radius: 999px;
  background: var(--c-accent-soft);
  color: var(--c-accent);
  font-weight: 800; font-size: 13px;
  letter-spacing: 0.04em;
  margin-bottom: 20px;
}
h1 {
  margin: 0;
  font-size: clamp(36px, 6vw, 64px);
  line-height: 1.1;
  letter-spacing: -0.03em;
  font-weight: 900;
}
.line { display: inline; }
.line-accent { color: var(--c-accent); }

.subtitle {
  margin: 20px 0 32px;
  max-width: 40ch;
  color: var(--c-sub);
  font-size: 17px;
  line-height: 1.6;
}
.hero-cta { display: flex; gap: 10px; flex-wrap: wrap; }

/* --- hero aside (right) — feature pills --- */
.hero-aside {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 14px;
  justify-self: end;
  padding-right: 24px;
}

.pill {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 18px 28px 18px 22px;
  border-radius: 14px;
  background: var(--c-surface);
  border: 1px solid var(--c-border);
  box-shadow: 0 4px 16px oklch(0.15 0.02 50 / 0.06);
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
              box-shadow 0.35s cubic-bezier(0.22, 1, 0.36, 1);
  will-change: transform;
}
.pill:hover {
  box-shadow: 0 12px 32px oklch(0.15 0.02 50 / 0.1);
}

.pill-1 { transform: translateX(24px); }
.pill-1:hover { transform: translateX(24px) translateY(-4px); }
.pill-2 { transform: translateX(-8px); }
.pill-2:hover { transform: translateX(-8px) translateY(-4px); }
.pill-3 { transform: translateX(40px); }
.pill-3:hover { transform: translateX(40px) translateY(-4px); }

.pill-num {
  font-size: 28px; font-weight: 900;
  letter-spacing: -0.03em; line-height: 1;
}
.pill-1 .pill-num { color: var(--c-accent); opacity: 0.5; }
.pill-2 .pill-num { color: var(--c-primary); opacity: 0.4; }
.pill-3 .pill-num { color: var(--c-teal); opacity: 0.55; }

.pill-label { font-size: 16px; font-weight: 700; }
/* --- entrance animations --- */
.hero-content,
.pill {
  opacity: 0;
  transition: opacity 0.7s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.7s cubic-bezier(0.22, 1, 0.36, 1);
}
.hero-content { transform: translateY(28px); }
.pill-1 { transform: translateY(28px) translateX(24px); }
.pill-2 { transform: translateY(28px) translateX(-8px); }
.pill-3 { transform: translateY(28px) translateX(40px); }

.entered .hero-content { opacity: 1; transform: none; transition-delay: 0.05s; }
.entered .pill-1 { opacity: 1; transform: translateX(24px); transition-delay: 0.25s; }
.entered .pill-2 { opacity: 1; transform: translateX(-8px); transition-delay: 0.35s; }
.entered .pill-3 { opacity: 1; transform: translateX(40px); transition-delay: 0.45s; }

.watermark {
  opacity: 0;
  transition: opacity 1.2s ease 0.3s;
}
.entered .watermark { opacity: 0.05; }

/* --- keyframes --- */
@keyframes drift1 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(-25px, 18px) scale(1.06); }
}
@keyframes drift2 {
  0%, 100% { transform: translate(0, 0) scale(1); }
  50% { transform: translate(20px, -12px) scale(1.04); }
}

/* --- responsive --- */
@media (max-width: 860px) {
  .hero {
    grid-template-columns: 1fr;
    min-height: auto;
    gap: 40px;
    padding: 24px 0 48px;
  }
  .hero-aside {
    justify-self: start;
    padding-right: 0;
  }
  .pill-1, .pill-2, .pill-3 { transform: none; }
  .pill-1:hover, .pill-2:hover, .pill-3:hover { transform: translateY(-4px); }
  .entered .pill-1 { transform: none; }
  .entered .pill-2 { transform: none; }
  .entered .pill-3 { transform: none; }
  .watermark { display: none; }
  .blob-1 { width: 240px; height: 240px; }
  .blob-2 { width: 180px; height: 180px; }
}

@media (max-width: 520px) {
  .nav { flex-direction: column; align-items: flex-start; }
  .nav-actions, .hero-cta { width: 100%; }
  .link-solid, .link-ghost { flex: 1; }
}

@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
