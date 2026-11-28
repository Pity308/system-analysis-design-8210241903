<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError, type Clinic } from '../api'

/**
 * UC-07 维护诊所信息。
 *
 * 报告 UC-07 的字段清单是「地址、主管姓名、电话、邮箱、办公面积」，加上名称、城市、
 * 开业日期、诊椅数与营业状态，共十项。其中诊所代码只在新建时可填——它是业务单号的
 * 前缀（UC-02 的“诊所代码 + 日期 + 四位流水”），建档后改代码会让历史单号对不上账。
 *
 * 关闭营业状态仍走“未来预约”校验，与列表行内按钮同一口径，两条入口不能一松一紧。
 */
const rows = ref<Clinic[]>([])
const err = ref('')
const okMsg = ref('')
const busy = ref(false)

/** null 表示“新建”，非 null 表示正在改这一家。 */
const editing = ref<Clinic | null>(null)
const creating = ref(false)

const form = reactive({
  clinicCode: '',
  clinicName: '',
  city: '',
  address: '',
  phone: '',
  managerName: '',
  email: '',
  areaSqm: '' as number | string,
  openDate: '',
  chairCount: '' as number | string,
  status: '筹备',
})

onMounted(load)

async function load() {
  rows.value = await api.clinics()
  // 默认打开第一家：右栏不留空，也省一次点击
  if (!editing.value && !creating.value && rows.value.length) startEdit(rows.value[0])
}

function reset() {
  form.clinicCode = ''
  form.clinicName = ''
  form.city = ''
  form.address = ''
  form.phone = ''
  form.managerName = ''
  form.email = ''
  form.areaSqm = ''
  form.openDate = ''
  form.chairCount = ''
  form.status = '筹备'
}

function startCreate() {
  err.value = ''
  okMsg.value = ''
  editing.value = null
  creating.value = true
  reset()
}

function startEdit(c: Clinic) {
  err.value = ''
  okMsg.value = ''
  creating.value = false
  editing.value = c
  form.clinicCode = c.clinicCode
  form.clinicName = c.clinicName
  form.city = c.city
  form.address = c.address || ''
  form.phone = c.phone || ''
  form.managerName = c.managerName || ''
  form.email = c.email || ''
  form.areaSqm = c.areaSqm ?? ''
  form.openDate = c.openDate || ''
  form.chairCount = c.chairCount ?? ''
  form.status = c.status
}

const formTitle = computed(() =>
  creating.value ? '新建诊所' : editing.value ? `编辑：${editing.value.clinicName}` : '新建 / 编辑',
)

function body() {
  return {
    clinicCode: form.clinicCode.trim().toUpperCase(),
    clinicName: form.clinicName.trim(),
    city: form.city.trim(),
    address: form.address.trim() || null,
    phone: form.phone.trim() || null,
    managerName: form.managerName.trim() || null,
    email: form.email.trim() || null,
    areaSqm: form.areaSqm === '' ? null : Number(form.areaSqm),
    openDate: form.openDate || null,
    chairCount: form.chairCount === '' ? null : Number(form.chairCount),
    status: form.status,
  }
}

async function save() {
  err.value = ''
  okMsg.value = ''
  busy.value = true
  try {
    if (creating.value) {
      const created = await api.createClinic(body())
      okMsg.value = `已新建“${created.clinicName}”（代码 ${created.clinicCode}，状态 ${created.status}）`
      creating.value = false
      startEdit(created)
    } else if (editing.value) {
      const updated = await api.updateClinic(editing.value.clinicId, body())
      okMsg.value = `“${updated.clinicName}”已保存`
      startEdit(updated)
    }
    await load()
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  } finally {
    busy.value = false
  }
}

async function toggle(c: Clinic) {
  err.value = ''
  okMsg.value = ''
  const next = c.status === '营业' ? '筹备' : '营业'
  try {
    await api.setClinicStatus(c.clinicId, next)
    okMsg.value = `${c.clinicName} 已改为“${next}”`
    await load()
    if (editing.value?.clinicId === c.clinicId) {
      const again = rows.value.find((r) => r.clinicId === c.clinicId)
      if (again) startEdit(again)
    }
  } catch (e) {
    err.value = e instanceof ApiError ? e.message : String(e)
  }
}
</script>

<template>
  <div class="page-head">
    <h2>诊所信息</h2>
    <span class="sub">UC-07 · 主执行者：总部管理者 · 关闭诊所前必须校验未来预约（服务端拦）</span>
  </div>

  <div v-if="err" class="notice error">{{ err }}</div>
  <div v-if="okMsg" class="notice ok">{{ okMsg }}</div>

  <div class="split" :class="creating || editing ? 'wide-right' : 'single'">
    <div class="card">
      <div class="card-title">
        诊所清单
        <span class="hint">共 {{ rows.length }} 家 · 代码用于拼接业务单号，不随改名变化</span>
      </div>
      <table class="data">
        <thead>
          <tr>
            <th>代码</th><th>诊所</th><th>主管</th><th>状态</th><th></th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="c in rows"
            :key="c.clinicId"
            class="clickable"
            :class="{ selected: editing?.clinicId === c.clinicId }"
            @click="startEdit(c)"
          >
            <td class="mono"><b>{{ c.clinicCode }}</b></td>
            <td>{{ c.clinicName }}</td>
            <td class="muted">{{ c.managerName || '—' }}</td>
            <td>
              <span class="tag" :class="c.status === '营业' ? 'green' : c.status === '筹备' ? 'amber' : 'gray'">
                {{ c.status }}
              </span>
            </td>
            <td class="right" @click.stop>
              <button class="btn sm" :disabled="busy || c.status === '关闭'" @click="toggle(c)">
                {{ c.status === '营业' ? '置为筹备' : '恢复营业' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
      <div class="divider"></div>
      <button class="btn" @click="startCreate">＋ 新建诊所</button>
    </div>

    <div v-if="creating || editing" class="card">
      <div class="card-title">
        {{ formTitle }}
        <span class="hint">代码保存后不可修改</span>
      </div>

      <div class="row" style="margin-bottom: 10px">
        <div class="field" style="flex: 1">
          <label>诊所代码<span class="req">*</span></label>
          <input v-model="form.clinicCode" :disabled="!creating" placeholder="如 GZZX01（3–6 位）" />
        </div>
        <div class="field" style="flex: 1.8">
          <label>诊所名称<span class="req">*</span></label>
          <input v-model="form.clinicName" placeholder="如 ADC 广州中新店" />
        </div>
      </div>

      <div class="row" style="margin-bottom: 10px">
        <div class="field">
          <label>所在城市<span class="req">*</span></label>
          <input v-model="form.city" placeholder="如 广州" />
        </div>
        <div class="field" style="flex: 2">
          <label>详细地址</label>
          <input v-model="form.address" placeholder="区 + 路 + 门牌" />
        </div>
      </div>

      <div class="row" style="margin-bottom: 10px">
        <div class="field" style="flex: 1.4">
          <label>主管姓名</label>
          <input v-model="form.managerName" placeholder="诊所主管（岗位）" />
        </div>
        <div class="field">
          <label>联系电话</label>
          <input v-model="form.phone" placeholder="带区号固话" />
        </div>
      </div>

      <div class="row" style="margin-bottom: 10px">
        <div class="field" style="flex: 1.6">
          <label>邮箱</label>
          <input v-model="form.email" placeholder="如 gzzx01@adc-dental.cn" />
        </div>
        <div class="field">
          <label>办公面积（㎡）</label>
          <input v-model="form.areaSqm" type="number" min="0" step="0.1" />
        </div>
      </div>

      <div class="row" style="margin-bottom: 12px">
        <div class="field">
          <label>开业日期</label>
          <input v-model="form.openDate" type="date" />
        </div>
        <div class="field">
          <label>诊椅数量</label>
          <input v-model="form.chairCount" type="number" min="1" />
        </div>
        <div class="field">
          <label>营业状态</label>
          <select v-model="form.status">
            <option>营业</option>
            <option>筹备</option>
            <option>关闭</option>
          </select>
        </div>
      </div>

      <div class="row">
        <button class="btn primary" :disabled="busy" @click="save">
          {{ creating ? '创建' : '保存修改' }}
        </button>
        <button class="btn" :disabled="busy" @click="startCreate">清空并新建</button>
      </div>
    </div>
  </div>
</template>
