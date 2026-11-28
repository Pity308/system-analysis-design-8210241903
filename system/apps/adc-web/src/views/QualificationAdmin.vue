<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  api,
  ApiError,
  type Clinic,
  type Dentist,
  type DentistClinic,
  type DentistQualification,
  type QualificationOption,
} from '../api'

/**
 * UC-06 维护牙医资质档案。
 *
 * 界面对应报告的分析类：左边是「牙医」清单，右边分两块——
 * 「资质」来自值表 qualification 与关联表 dentist_qualification，
 * 「执业点」来自关联表 dentist_clinic。三块都是“一对多”，
 * 所以必须点开某位牙医才能维护，不能在清单里直接编辑。
 *
 * 页面上的按钮只是入口，所有规则（重复授予、主执业点唯一、撤销前校验未来预约）
 * 都在服务端；这里只负责把拒绝原因原样显示出来。
 */
const rows = ref<Dentist[]>([])
const dict = ref<QualificationOption[]>([])
const clinics = ref<Clinic[]>([])

const selected = ref<Dentist | null>(null)
const dqs = ref<DentistQualification[]>([])
const dcs = ref<DentistClinic[]>([])

const err = ref('')
const okMsg = ref('')
const busy = ref(false)

const profile = reactive({ licenseNo: '', specialty: '', title: '' })
const grant = reactive<{ qualificationId: number | ''; obtainDate: string; certificateNo: string }>({
  qualificationId: '',
  obtainDate: today(),
  certificateNo: '',
})
const newClinic = reactive<{ clinicId: number | ''; scheduleNote: string; primary: boolean }>({
  clinicId: '',
  scheduleNote: '',
  primary: false,
})

function today() {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  const [ds, qs, cs] = await Promise.all([api.dentists(), api.qualifications(), api.clinics()])
  rows.value = ds
  dict.value = qs
  clinics.value = cs
  if (ds.length) await select(ds[0])
})

async function select(d: Dentist) {
  err.value = ''
  okMsg.value = ''
  selected.value = d
  profile.licenseNo = d.licenseNo || ''
  profile.specialty = d.specialty || ''
  profile.title = d.title || ''
  grant.qualificationId = ''
  grant.certificateNo = ''
  grant.obtainDate = today()
  newClinic.clinicId = ''
  newClinic.scheduleNote = ''
  newClinic.primary = false
  await refreshDetail()
}

async function refreshDetail() {
  if (!selected.value) return
  const id = selected.value.employeeId
  const [q, c] = await Promise.all([api.dentistQualifications(id), api.dentistClinics(id)])
  dqs.value = q
  dcs.value = c
}

/** 写操作统一收口：清提示 → 调接口 → 刷新明细与左侧汇总。 */
async function run(action: () => Promise<unknown>, successText: string) {
  err.value = ''
  okMsg.value = ''
  busy.value = true
  try {
    await action()
    const keep = selected.value!.employeeId
    rows.value = await api.dentists()
    const again = rows.value.find((d) => d.employeeId === keep)
    if (again) selected.value = again
    await refreshDetail()
    okMsg.value = successText
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

function saveProfile() {
  if (!selected.value) return
  run(
    () =>
      api.updateDentist(selected.value!.employeeId, {
        licenseNo: profile.licenseNo.trim(),
        specialty: profile.specialty.trim() || undefined,
        title: profile.title.trim() || undefined,
      }),
    '牙医基本信息已保存',
  )
}

const grantable = computed(() => {
  const held = new Set(dqs.value.map((q) => q.qualificationId))
  return dict.value.filter((q) => !held.has(q.qualificationId))
})

function doGrant() {
  if (!selected.value) return
  if (grant.qualificationId === '') {
    err.value = '请选择要授予的资质'
    return
  }
  run(
    () =>
      api.grantQualification(selected.value!.employeeId, {
        qualificationId: Number(grant.qualificationId),
        obtainDate: grant.obtainDate || undefined,
        certificateNo: grant.certificateNo.trim() || undefined,
      }),
    '资质已授予',
  )
}

function doRevoke(q: DentistQualification) {
  if (!selected.value) return
  if (!confirm(`确认撤销“${q.qualificationName}”？若该牙医还有涉及此资质的未来预约，服务端会拒绝。`)) return
  run(() => api.revokeQualification(selected.value!.employeeId, q.qualificationId), '资质已撤销')
}

function doAddClinic() {
  if (!selected.value) return
  if (newClinic.clinicId === '') {
    err.value = '请选择诊所'
    return
  }
  run(
    () =>
      api.addDentistClinic(selected.value!.employeeId, {
        clinicId: Number(newClinic.clinicId),
        scheduleNote: newClinic.scheduleNote.trim() || undefined,
        primary: newClinic.primary,
      }),
    '执业点已登记',
  )
}

function doRemoveClinic(c: DentistClinic) {
  if (!selected.value) return
  if (!confirm(`确认撤销“${c.clinicName}”执业点？该诊所若有该牙医的未来预约，服务端会拒绝。`)) return
  run(() => api.removeDentistClinic(selected.value!.employeeId, c.clinicId), '执业点已撤销')
}

/** 报告 UC-06 约束：到期前 60 天高亮。 */
function daysLeft(d?: string | null) {
  if (!d) return null
  const diff = new Date(d + 'T00:00:00').getTime() - Date.now()
  return Math.ceil(diff / 86400000)
}

/** 清单里只放得下一项，多项时给“等 n 项”，完整清单在 title 与右栏。 */
function qualBrief(d: Dentist) {
  const list = (d.qualificationNames || '').split('、').filter(Boolean)
  if (!list.length) return '—'
  return list.length === 1 ? list[0] : `${list[0]} 等 ${list.length} 项`
}

const expiring = computed(() =>
  rows.value.filter((r) => {
    const d = daysLeft(r.earliestExpiry)
    return d !== null && d <= 60
  }),
)

const clinicsOfDentist = computed(() => {
  const taken = new Set(dcs.value.map((c) => c.clinicId))
  return clinics.value.filter((c) => !taken.has(c.clinicId) && c.status !== '关闭')
})
</script>

<template>
  <div class="page-head">
    <h2>资质维护</h2>
    <span class="sub">UC-06 · 主执行者：总部管理者 · 牙医资质 + 执业点轮转，撤销前校验未来预约</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div v-if="expiring.length" class="notice warn">
    有 {{ expiring.length }} 位牙医的资质将在 60 天内到期：{{ expiring.map((e) => e.fullName).join('、') }}
  </div>

  <div class="split wide-right">
    <!-- 左：牙医清单 -->
    <div class="card">
      <div class="card-title">
        牙医 {{ rows.length }} 人
        <span class="hint">点一行进入维护</span>
      </div>
      <table class="data">
        <thead>
          <tr><th>编号</th><th>姓名</th><th>职称</th><th>资质</th></tr>
        </thead>
        <tbody>
          <tr
            v-for="d in rows"
            :key="d.employeeId"
            class="clickable"
            :class="{ selected: selected?.employeeId === d.employeeId }"
            @click="select(d)"
          >
            <td class="mono">{{ d.employeeId }}</td>
            <td>{{ d.fullName }}</td>
            <td class="muted">{{ d.title || '—' }}</td>
            <td class="muted" :title="d.qualificationNames || ''">{{ qualBrief(d) }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 右：某位牙医的档案 -->
    <div v-if="selected">
      <div class="card">
        <div class="card-title">
          {{ selected.fullName }} · 基本信息
          <span class="hint">执业证书编号全连锁唯一，改号会被查重挡住</span>
        </div>
        <div class="row" style="margin-bottom: 10px">
          <div class="field" style="flex: 1.4">
            <label>执业证书编号<span class="req">*</span></label>
            <input v-model="profile.licenseNo" placeholder="如 GD-DEN-2016-0142" />
          </div>
          <div class="field">
            <label>职称</label>
            <input v-model="profile.title" placeholder="如 主治医师" />
          </div>
        </div>
        <div class="row">
          <div class="field" style="flex: 2">
            <label>专业方向</label>
            <input v-model="profile.specialty" placeholder="如 牙体牙髓、种植、修复" />
          </div>
          <button class="btn primary" :disabled="busy" @click="saveProfile">保存</button>
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          持有资质 {{ dqs.length }} 项
          <span class="hint">关联表上记录取得日期与证书编号，不是只存一个名字</span>
        </div>
        <table v-if="dqs.length" class="data">
          <thead>
            <tr><th>资质</th><th>颁发机构</th><th>取得日期</th><th>证书编号</th><th>有效期至</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="q in dqs" :key="q.dqId">
              <td>{{ q.qualificationName }}</td>
              <td class="muted">{{ q.issuingAuthority || '—' }}</td>
              <td class="mono">{{ q.obtainDate || '—' }}</td>
              <td class="mono">{{ q.certificateNo || '—' }}</td>
              <td>
                <span v-if="!q.validUntil" class="muted">不限</span>
                <span v-else class="tag" :class="(daysLeft(q.validUntil) ?? 999) <= 60 ? 'amber' : 'gray'">
                  {{ q.validUntil }}
                  <template v-if="(daysLeft(q.validUntil) ?? 999) <= 60">（剩 {{ daysLeft(q.validUntil) }} 天）</template>
                </span>
              </td>
              <td class="right">
                <button class="btn sm" :disabled="busy" @click="doRevoke(q)">撤销</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">尚未授予任何资质 —— 无资质的牙医不能接诊需要资质的治疗项目。</div>

        <div class="divider"></div>
        <div class="row">
          <div class="field" style="flex: 1.6">
            <label>授予资质</label>
            <select v-model="grant.qualificationId" :disabled="!grantable.length">
              <option value="">选择资质…</option>
              <option v-for="q in grantable" :key="q.qualificationId" :value="q.qualificationId">
                {{ q.qualificationName }}
              </option>
            </select>
          </div>
          <div class="field">
            <label>取得日期</label>
            <input v-model="grant.obtainDate" type="date" />
          </div>
          <div class="field">
            <label>证书编号</label>
            <input v-model="grant.certificateNo" placeholder="选填" />
          </div>
          <button class="btn primary" :disabled="busy || !grantable.length" @click="doGrant">授予</button>
        </div>
        <div v-if="!grantable.length" class="muted" style="font-size: 11.5px">
          资质字典里的项目都已持有；要新增资质种类请维护 qualification 值表。
        </div>
      </div>

      <div class="card">
        <div class="card-title">
          执业点 {{ dcs.length }} 个
          <span class="hint">is_primary 是关联表上的属性，全局只能有一个</span>
        </div>
        <table v-if="dcs.length" class="data">
          <thead>
            <tr><th>代码</th><th>诊所</th><th>排班说明</th><th>主要</th><th></th></tr>
          </thead>
          <tbody>
            <tr v-for="c in dcs" :key="c.dcId">
              <td class="mono">{{ c.clinicCode }}</td>
              <td>{{ c.clinicName }}</td>
              <td class="muted">{{ c.scheduleNote || '—' }}</td>
              <td>
                <span v-if="c.primary" class="tag green">主执业点</span>
                <span v-else class="muted">轮转</span>
              </td>
              <td class="right">
                <button class="btn sm" :disabled="busy" @click="doRemoveClinic(c)">撤销</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">尚未登记执业点 —— 没有执业点的牙医不会出现在可约时段里。</div>

        <div class="divider"></div>
        <div class="row">
          <div class="field" style="flex: 1.6">
            <label>登记执业点</label>
            <select v-model="newClinic.clinicId">
              <option value="">选择诊所…</option>
              <option v-for="c in clinicsOfDentist" :key="c.clinicId" :value="c.clinicId">
                {{ c.clinicCode }} · {{ c.clinicName }}
              </option>
            </select>
          </div>
          <div class="field" style="flex: 1.4">
            <label>排班说明</label>
            <input v-model="newClinic.scheduleNote" placeholder="如 周二、周四全天" />
          </div>
          <div class="field" style="flex: 0 0 96px">
            <label>主要</label>
            <select v-model="newClinic.primary">
              <option :value="false">否</option>
              <option :value="true">是</option>
            </select>
          </div>
          <button class="btn primary" :disabled="busy || !clinicsOfDentist.length" @click="doAddClinic">登记</button>
        </div>
      </div>
    </div>
  </div>
</template>
