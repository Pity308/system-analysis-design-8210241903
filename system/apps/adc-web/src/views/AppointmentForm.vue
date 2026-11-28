<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { api, ApiError, type Clinic, type Patient, type Slot, type TreatmentItem } from '../api'
import { session } from '../stores/session'

const clinics = ref<Clinic[]>([])
const items = ref<TreatmentItem[]>([])
const phone = ref('')
const patients = ref<Patient[]>([])
const patient = ref<Patient | null>(null)
const searched = ref(false)

const clinicId = ref<number>(session.clinicId)
const date = ref(today())
const itemIds = ref<number[]>([])
const minutes = ref(30)
const slots = ref<Slot[]>([])
const picked = ref<Slot | null>(null)
const channel = ref('电话')
const note = ref('')

const loadingSlots = ref(false)
const submitting = ref(false)
const err = ref('')
const ok = ref<any>(null)
const warnings = ref<string[]>([])

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  clinics.value = await api.clinics()
  items.value = (await api.treatmentItems()).filter((i) => i.active)
  if (!clinics.value.some((c) => c.clinicId === clinicId.value)) {
    clinicId.value = clinics.value[0]?.clinicId ?? 1
  }
})

const requiredQuals = computed(() => {
  const s = new Set<number>()
  itemIds.value.forEach((id) => {
    const it = items.value.find((x) => x.itemId === id)
    if (it?.requiredQualificationId) s.add(it.requiredQualificationId)
  })
  return [...s]
})

const groupedSlots = computed(() => {
  const map = new Map<string, Slot[]>()
  slots.value.forEach((s) => {
    const k = `${s.employeeId}|${s.dentistName}|${s.title}|${s.clinicName}`
    if (!map.has(k)) map.set(k, [])
    map.get(k)!.push(s)
  })
  return [...map.entries()].map(([k, v]) => {
    const [employeeId, name, title, clinicName] = k.split('|')
    return { employeeId: Number(employeeId), name, title, clinicName, list: v }
  })
})

async function searchPatient() {
  err.value = ''
  ok.value = null
  if (!phone.value.trim()) {
    err.value = '请输入患者电话'
    return
  }
  patients.value = await api.patientByPhone(phone.value.trim())
  searched.value = true
  patient.value = patients.value[0] ?? null
}

function choosePatient(p: Patient) {
  patient.value = p
}

async function querySlots() {
  err.value = ''
  ok.value = null
  picked.value = null
  if (!date.value) {
    err.value = '请选择就诊日期'
    return
  }
  loadingSlots.value = true
  try {
    slots.value = await api.availability({
      date: date.value,
      clinicId: clinicId.value,
      treatmentItemIds: itemIds.value,
      minutes: minutes.value,
    })
    if (!slots.value.length) {
      err.value = '该日该诊所没有满足条件的可约时段，换个日期或诊所再试（扩展路径 4a）。'
    }
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    loadingSlots.value = false
  }
}

async function submit() {
  err.value = ''
  ok.value = null
  warnings.value = []
  if (!patient.value) {
    err.value = '请先检索并选中患者（新患者请先做 UC-01 建档）'
    return
  }
  if (!picked.value) {
    err.value = '请选择一个可约时段'
    return
  }
  submitting.value = true
  try {
    const res = await api.createAppointment({
      patientId: patient.value.patientId,
      employeeId: picked.value.employeeId,
      clinicId: picked.value.clinicId,
      appointmentTime: picked.value.start,
      expectedMinutes: minutes.value,
      channel: channel.value,
      treatmentItemIds: itemIds.value,
      note: note.value || null,
    })
    ok.value = res.appointment
    warnings.value = res.warnings || []
    slots.value = []
    picked.value = null
  } catch (e) {
    if (e instanceof ApiError) err.value = e.message
    else err.value = String(e)
    // 冲突说明时段刚被抢走，直接帮前台刷新一次
    if (e instanceof ApiError && e.status === 409) await querySlots()
  } finally {
    submitting.value = false
  }
}

function fmt(t: string) {
  return t.slice(11, 16)
}

watch([clinicId, date, minutes], () => {
  slots.value = []
  picked.value = null
})
</script>

<template>
  <div class="page-head">
    <h2>预约登记</h2>
    <span class="sub">UC-02 · 资质匹配 + 时段排他，均由服务端判定，界面只做呈现</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-for="w in warnings" :key="w" class="notice warn">{{ w }}</div>

  <div v-if="ok" class="notice ok">
    预约成功：<b class="mono">{{ ok.appointmentNo }}</b> ·
    {{ ok.patientName }} · {{ ok.appointmentTime?.replace('T', ' ').slice(0, 16) }} ·
    {{ ok.dentistName }}（{{ ok.clinicName }}）· 状态 {{ ok.status }}
  </div>

  <div class="split">
    <div>
      <!-- 步骤 1：患者 -->
      <div class="card">
        <div class="card-title">1. 调取患者档案<span class="hint">步骤 1~2</span></div>
        <div class="row">
          <div class="field" style="flex: 2">
            <label>电话号码</label>
            <input v-model="phone" placeholder="患者来电号码" @keyup.enter="searchPatient" />
          </div>
          <button class="btn primary" @click="searchPatient">调取</button>
        </div>

        <div v-if="searched" style="margin-top: 10px">
          <div v-if="!patients.length" class="notice warn">
            系统提示该患者为新患者（扩展路径 1a）—— 请先到
            <a href="#/reception/patient">患者建档</a> 完成 UC-01，再回到本页受理。
          </div>
          <table v-else class="data">
            <thead>
              <tr><th>编号</th><th>姓名</th><th>性别</th><th>出生</th><th>过敏症</th><th></th></tr>
            </thead>
            <tbody>
              <tr
                v-for="p in patients"
                :key="p.patientId"
                class="clickable"
                :class="{ selected: patient?.patientId === p.patientId }"
                @click="choosePatient(p)"
              >
                <td class="mono">{{ p.patientId }}</td>
                <td>{{ p.fullName }}</td>
                <td>{{ p.gender }}</td>
                <td>{{ p.birthDate || '—' }}</td>
                <td>
                  <span v-if="p.allergies?.length" class="tag red">
                    {{ p.allergies.map((a) => a.allergenName).join('、') }}
                  </span>
                  <span v-else class="muted">无</span>
                </td>
                <td class="right"><span v-if="patient?.patientId === p.patientId" class="tag green">已选</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 步骤 2：需求与时段 -->
      <div class="card">
        <div class="card-title">2. 就诊需求与可约时段<span class="hint">步骤 3~4</span></div>
        <div class="grid-3" style="margin-bottom: 10px">
          <div class="field">
            <label>接诊诊所</label>
            <select v-model.number="clinicId">
              <option v-for="c in clinics" :key="c.clinicId" :value="c.clinicId">
                {{ c.clinicName }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>就诊日期</label>
            <input v-model="date" type="date" />
          </div>
          <div class="field">
            <label>预计时长</label>
            <select v-model.number="minutes">
              <option :value="30">30 分钟</option>
              <option :value="60">60 分钟</option>
              <option :value="90">90 分钟</option>
              <option :value="120">120 分钟</option>
            </select>
          </div>
        </div>

        <div class="field">
          <label>治疗需求（可多选，参与牙医资质匹配）</label>
          <div style="display: flex; gap: 6px; flex-wrap: wrap; padding-top: 2px">
            <label
              v-for="it in items"
              :key="it.itemId"
              class="tag"
              :class="itemIds.includes(it.itemId) ? 'green' : 'gray'"
              style="cursor: pointer; padding: 5px 10px"
            >
              <input
                v-model="itemIds"
                type="checkbox"
                :value="it.itemId"
                style="display: none"
              />
              {{ it.itemName }} · ￥{{ it.unitPrice }}
            </label>
          </div>
          <div v-if="requiredQuals.length" class="muted" style="margin-top: 6px">
            需同时具备资质编号 {{ requiredQuals.join('、') }} 的牙医才会出现在下方列表
          </div>
        </div>

        <button class="btn primary" :disabled="loadingSlots" @click="querySlots">
          {{ loadingSlots ? '查询中…' : '查询可约时段' }}
        </button>

        <div v-if="groupedSlots.length" style="margin-top: 14px">
          <div v-for="g in groupedSlots" :key="g.employeeId" style="margin-bottom: 12px">
            <div style="display: flex; gap: 8px; align-items: baseline; margin-bottom: 6px">
              <b>{{ g.name }}</b>
              <span class="muted">{{ g.title }} · {{ g.clinicName }}</span>
            </div>
            <div class="slot-grid">
              <button
                v-for="s in g.list"
                :key="s.start"
                class="slot"
                :class="{ active: picked?.start === s.start && picked?.employeeId === s.employeeId }"
                @click="picked = s"
              >
                <div class="t">{{ fmt(s.start) }} – {{ fmt(s.end) }}</div>
                <div class="d">{{ g.name }}</div>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤 3：提交 -->
    <div class="card" style="position: sticky; top: 0">
      <div class="card-title">3. 确认预约<span class="hint">步骤 5~7</span></div>

      <div class="field">
        <label>患者</label>
        <input :value="patient ? `${patient.fullName}（${patient.patientId}）` : ''" disabled placeholder="尚未选择" />
      </div>
      <div class="field">
        <label>牙医 / 时段</label>
        <input
          :value="picked ? `${picked.dentistName} · ${picked.start.replace('T', ' ').slice(0, 16)}` : ''"
          disabled
          placeholder="尚未选择"
        />
      </div>
      <div class="field">
        <label>预约渠道</label>
        <select v-model="channel">
          <option>电话</option>
          <option>到店</option>
          <option>小程序</option>
        </select>
      </div>
      <div class="field">
        <label>就诊备注</label>
        <textarea v-model="note" placeholder="患者主诉、来源（如“朋友介绍”）、特殊要求"></textarea>
      </div>

      <div class="notice info" style="font-size: 12.5px">
        预约状态一律从“待就诊”开始；单号按“诊所代码 + 日期 + 四位流水”生成，
        时段冲突由数据库排他约束兜底，抢不到会直接提示重选。
      </div>

      <button class="btn primary" :disabled="submitting" style="width: 100%" @click="submit">
        {{ submitting ? '提交中…' : '提交预约' }}
      </button>
    </div>
  </div>
</template>
