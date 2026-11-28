<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError, type StaffProfile } from '../api'
import { auth } from '../api/http'
import { applyProfile } from '../stores/session'

const router = useRouter()
const route = useRoute()

const form = reactive({ username: '', password: '' })
const err = ref('')
const loading = ref(false)
const dbOk = ref<boolean | null>(null)

/** 演示账号：课程演示用，写在这里比写在 PDF 里好找。上线前必须删掉这一块。 */
const demoAccounts = [
  { username: 'linxiaoyu', role: '前台接待员', scope: '收银台四窗体' },
  { username: 'chenliqun', role: '牙医', scope: '只到“到诊与治疗”' },
  { username: 'zhengwei', role: '总部管理者', scope: '资质、诊所、报表' },
]

onMounted(async () => {
  try {
    const h = await api.health()
    dbOk.value = h.status === 'UP'
  } catch {
    dbOk.value = false
  }
})

function fill(username: string) {
  form.username = username
  form.password = '123456'
}

function roleHome(p: StaffProfile) {
  switch (p.roleCode) {
    case 'DENTIST':
      return '/dentist/visit'
    case 'ADMIN':
      return '/hq/quarter'
    default:
      return '/reception/patient'
  }
}

async function submit() {
  if (loading.value) return
  err.value = ''
  if (!form.username.trim() || !form.password) {
    err.value = '请输入用户名与口令'
    return
  }
  loading.value = true
  try {
    const res = await api.login(form.username.trim(), form.password)
    auth.save(res.token)
    applyProfile(res.profile)
    const next = (route.query.next as string) || ''
    // 只接受站内的普通路径，避免 next 被塞成外站地址
    const safeNext = next.startsWith('/') && !next.startsWith('//') ? next : ''
    router.replace(safeNext || roleHome(res.profile))
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
    form.password = ''
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-wrap">
    <section class="login-left">
      <div class="mark">ADC</div>
      <h1>安德鲁牙科连锁</h1>
      <p class="sub">ANDREW DENTAL CHAIN · 信息管理系统</p>

      <ul class="points">
        <li><b>前台收银台</b>建档、预约、到诊、收费，一条价值链走完</li>
        <li><b>牙医工作台</b>只看得到自己的当日就诊与治疗记录</li>
        <li><b>总部管理台</b>资质、诊所、季度业绩与经营报表</li>
      </ul>

      <p class="foot">系统分析与设计 · 课程设计实现</p>
    </section>

    <section class="login-right">
      <div class="form-card">
        <h2>员工登录</h2>
        <p class="tip">
          <i class="dot" :style="{ background: dbOk === false ? '#b93b45' : '#12876f' }"></i>
          {{ dbOk === false ? '后端未连接' : dbOk === null ? '正在检查服务…' : '服务正常' }}
        </p>

        <div v-if="err" class="err">{{ err }}</div>

        <form @submit.prevent="submit">
          <label>用户名</label>
          <input v-model="form.username" autocomplete="username" placeholder="例如 linxiaoyu" />

          <label>口令</label>
          <input
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入登录口令"
          />

          <button class="submit" type="submit" :disabled="loading">
            {{ loading ? '登录中…' : '登录' }}
          </button>
        </form>

        <div class="demo">
          <div class="demo-title">演示账号（点击填入账号，口令见交接文档 15.1）</div>
          <div
            v-for="a in demoAccounts"
            :key="a.username"
            class="demo-row"
            @click="fill(a.username)"
          >
            <code>{{ a.username }}</code>
            <span>{{ a.role }}</span>
            <em>{{ a.scope }}</em>
          </div>
        </div>

        <p class="note">
          口令以 BCrypt 哈希存储；角色写在服务端签发的票据里，前端改不了。
          患者请用微信小程序，此入口仅供员工。
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-wrap {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
  background: var(--bg);
}

.login-left {
  background: linear-gradient(150deg, #10233f 0%, #16365c 55%, #1d4b7f 100%);
  color: #fff;
  padding: 64px 56px;
  display: flex;
  flex-direction: column;
}

.mark {
  width: 64px;
  height: 64px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.22);
  display: grid;
  place-items: center;
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 28px;
}

.login-left h1 {
  font-size: 30px;
  letter-spacing: 1px;
}

.sub {
  margin: 8px 0 40px;
  color: rgba(255, 255, 255, 0.62);
  font-size: 12px;
  letter-spacing: 1.6px;
}

.points {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 18px;
}

.points li {
  padding-left: 16px;
  border-left: 2px solid rgba(255, 255, 255, 0.28);
  color: rgba(255, 255, 255, 0.86);
  font-size: 13.5px;
  line-height: 1.7;
}

.points b {
  display: block;
  color: #fff;
  font-size: 14.5px;
  margin-bottom: 2px;
}

.foot {
  margin-top: auto;
  color: rgba(255, 255, 255, 0.45);
  font-size: 12px;
}

.login-right {
  display: grid;
  place-items: center;
  padding: 40px 24px;
}

.form-card {
  width: 100%;
  max-width: 392px;
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 30px 30px 24px;
  box-shadow: var(--shadow);
}

.form-card h2 {
  font-size: 20px;
}

.tip {
  margin: 8px 0 20px;
  font-size: 12px;
  color: var(--ink-3);
  display: flex;
  align-items: center;
  gap: 7px;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  display: inline-block;
}

.err {
  background: #fdecec;
  border: 1px solid #f3c6c6;
  color: var(--adc-red);
  font-size: 12.5px;
  padding: 9px 11px;
  border-radius: 8px;
  margin-bottom: 14px;
}

form label {
  display: block;
  font-size: 12.5px;
  color: var(--ink-2);
  margin: 14px 0 6px;
}

form input {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fbfcfe;
  font-size: 13.5px;
  color: var(--ink);
  outline: none;
}

form input:focus {
  border-color: var(--adc-blue-500);
  background: #fff;
}

.submit {
  width: 100%;
  height: 42px;
  margin-top: 20px;
  border: 0;
  border-radius: 8px;
  background: var(--adc-blue-800);
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.submit:disabled {
  background: #9fb0c6;
  cursor: default;
}

.demo {
  margin-top: 22px;
  border-top: 1px dashed var(--line);
  padding-top: 16px;
}

.demo-title {
  font-size: 11.5px;
  color: var(--ink-3);
  margin-bottom: 8px;
}

.demo-row {
  display: grid;
  grid-template-columns: 96px 74px 1fr;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 12px;
  color: var(--ink-2);
}

.demo-row:hover {
  background: var(--adc-blue-100);
}

.demo-row code {
  font-size: 11.5px;
  color: var(--adc-blue-700);
}

.demo-row em {
  font-style: normal;
  color: var(--ink-3);
  font-size: 11.5px;
}

.note {
  margin: 18px 0 0;
  font-size: 11px;
  color: var(--ink-3);
  line-height: 1.7;
}

@media (max-width: 900px) {
  .login-wrap {
    grid-template-columns: 1fr;
  }
  .login-left {
    display: none;
  }
}
</style>
