<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, type Dentist } from './api'
import { session, signOut, workspacesOf } from './stores/session'

const route = useRoute()
const router = useRouter()

/** 三工作台：只显示当前角色能进的（报告 6.14 的第一道闸），真正的拒绝在服务端。 */
const allWorkspaces = [
  { key: 'reception', name: '前台收银台', entry: '/reception/patient' },
  { key: 'dentist', name: '牙医工作台', entry: '/dentist/visit' },
  { key: 'hq', name: '总部管理台', entry: '/hq/clinic' },
] as const

const navs: Record<string, { path: string; title: string; icon: string; hint: string }[]> = {
  reception: [
    { path: '/reception/patient', title: '患者建档', icon: '👤', hint: 'UC-01' },
    { path: '/reception/appointment', title: '预约登记', icon: '📅', hint: 'UC-02' },
    { path: '/reception/arrival', title: '到诊登记', icon: '🪑', hint: 'UC-05' },
    { path: '/reception/statement', title: '结算与收款', icon: '💳', hint: 'UC-04' },
  ],
  dentist: [{ path: '/dentist/visit', title: '到诊与治疗', icon: '🦷', hint: 'UC-03' }],
  hq: [
    { path: '/hq/clinic', title: '诊所信息', icon: '🏥', hint: 'UC-07' },
    { path: '/hq/qualification', title: '资质维护', icon: '📜', hint: 'UC-06' },
    { path: '/hq/quarter', title: '季度业绩', icon: '📈', hint: 'UC-08' },
    { path: '/hq/operation', title: '经营报表', icon: '📊', hint: 'UC-09' },
  ],
}

const isLogin = computed(() => route.path === '/login')
const allowed = computed(() => workspacesOf(session.profile?.roleCode))
const workspaces = computed(() => allWorkspaces.filter((w) => allowed.value.includes(w.key)))

const activeWs = computed(() => (route.meta.ws as string) || 'reception')
const navList = computed(() => navs[activeWs.value] || [])
const canSwitchDentist = computed(() => session.profile?.roleCode === 'ADMIN')

const dentists = ref<Dentist[]>([])
const dbOk = ref<boolean | null>(null)
const dbInfo = ref('')

onMounted(async () => {
  try {
    const h = await api.health()
    dbOk.value = h.status === 'UP'
    dbInfo.value = `${h.database} · ${h.businessAndSupportTables} 张表`
  } catch {
    dbOk.value = false
    dbInfo.value = '后端未启动'
  }
  await loadDentists()
})

/** 牙医目录：管理员要切人，牙医只看得到同事项；前台与未登录不请求（也没权限）。 */
async function loadDentists() {
  const role = session.profile?.roleCode
  if (role !== 'ADMIN' && role !== 'DENTIST') {
    dentists.value = []
    return
  }
  try {
    dentists.value = await api.dentists()
  } catch {
    dentists.value = []
  }
}

watch(() => session.profile?.roleCode, () => loadDentists())

// 路由前缀决定工作台高亮，切工作台跳到该台的第一个窗体
watch(
  () => route.path,
  () => {
    session.workspace = activeWs.value as any
  },
  { immediate: true },
)

function switchWs(key: string) {
  const w = workspaces.value.find((x) => x.key === key)
  if (w) router.push(w.entry)
}

function switchDentist(id: number) {
  const d = dentists.value.find((x) => x.employeeId === id)
  if (d) {
    session.dentistId = d.employeeId
    session.dentistName = d.fullName
  }
}
</script>

<template>
  <!-- 登录页不套壳：没有工作台，也没有导航 -->
  <router-view v-if="isLogin" />

  <div v-else class="shell">
    <header class="topbar">
      <div class="brand">
        <div class="brand-mark">ADC</div>
        <div class="brand-text">
          <b>安德鲁牙科连锁</b>
          <span>ANDREW DENTAL CHAIN</span>
        </div>
      </div>

      <div class="ws-tabs">
        <button
          v-for="w in workspaces"
          :key="w.key"
          class="ws-tab"
          :class="{ active: activeWs === w.key }"
          @click="switchWs(w.key)"
        >
          {{ w.name }}
        </button>
      </div>

      <div class="topbar-right">
        <span v-if="activeWs === 'dentist' && canSwitchDentist">
          接诊牙医
          <select
            style="display: inline-block; width: auto; margin-left: 6px; padding: 3px 8px; font-size: 12px"
            :value="session.dentistId"
            @change="switchDentist(Number(($event.target as HTMLSelectElement).value))"
          >
            <option v-for="d in dentists" :key="d.employeeId" :value="d.employeeId">
              {{ d.fullName }}（{{ d.clinicNames }}）
            </option>
          </select>
        </span>

        <span class="who" v-if="session.profile">
          <b>{{ session.profile.fullName }}</b>
          <em>{{ session.profile.roleName }}<template v-if="session.profile.clinicName"> · {{ session.profile.clinicName }}</template></em>
        </span>
        <button class="btn-mini" @click="signOut">退出</button>

        <span class="health"><i class="dot" :style="{ background: dbOk === false ? '#f08a8a' : '#46d39a' }"></i>{{ dbInfo }}</span>
      </div>
    </header>

    <aside class="sidebar">
      <div class="side-group-title">{{ workspaces.find((w) => w.key === activeWs)?.name }}</div>
      <div
        v-for="n in navList"
        :key="n.path"
        class="nav-item"
        :class="{ active: route.path === n.path }"
        @click="router.push(n.path)"
      >
        <span class="nav-icon">{{ n.icon }}</span>
        <span>{{ n.title }}</span>
        <span class="muted" style="margin-left: auto; font-size: 10.5px">{{ n.hint }}</span>
      </div>

      <div class="divider"></div>
      <div class="muted" style="padding: 0 10px; line-height: 1.7; font-size: 11.5px">
        窗体划分依据报告 6.13 图 14：员工侧九个用例各对应一个窗体；UC-10 落在患者端小程序，不在本导航内。
        <template v-if="session.profile?.roleCode === 'DENTIST'">
          牙医只看得到“到诊与治疗”，结算与总部报表由角色闸门挡住。
        </template>
      </div>
    </aside>

    <main class="main">
      <router-view :key="route.path" />
    </main>
  </div>
</template>
