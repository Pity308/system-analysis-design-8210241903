import { reactive } from 'vue'
import { api, type StaffProfile } from '../api'
import { auth } from '../api/http'

/**
 * 会话状态。
 * <p>
 * 报告 6.14 说“把权限的第一道闸放在入口”：菜单按角色收窄、路由按角色拦截。
 * 但这只是可用性——真正的拒绝在服务端（@RequireRole + 拦截器），
 * 前端藏起来的按钮，改个 URL 也调不通。
 * <p>
 * 票过期或 401 由 http.ts 统一清票跳登录页，这里只管“我是谁”。
 */
export const session = reactive({
  /** 员工档案；未登录为 null。 */
  profile: null as StaffProfile | null,
  /** 是否已完成一次身份恢复（刷新页面时避免闪一下登录页）。 */
  ready: false,
  /** 当前工作台，由角色决定，也可在允许范围内切换。 */
  workspace: 'reception' as 'reception' | 'dentist' | 'hq',
  /** 牙医工作台：默认就是登录者本人。 */
  dentistId: 0,
  dentistName: '',
  /** 前台默认受理的诊所：取登录者的所属诊所。 */
  clinicId: 1,
})

/** 角色能进哪些工作台。与后端 @RequireRole 的口径一一对应。 */
export function workspacesOf(role?: string): Array<'reception' | 'dentist' | 'hq'> {
  switch (role) {
    case 'RECEPTIONIST':
      return ['reception']
    case 'DENTIST':
      return ['dentist']
    case 'ADMIN':
      return ['reception', 'dentist', 'hq']
    default:
      return []
  }
}

export function applyProfile(p: StaffProfile) {
  session.profile = p
  if (p.clinicId) session.clinicId = p.clinicId
  if (p.roleCode === 'DENTIST') {
    session.dentistId = p.employeeId
    session.dentistName = p.fullName
  }
  const allowed = workspacesOf(p.roleCode)
  if (!allowed.includes(session.workspace)) {
    session.workspace = allowed[0] || 'reception'
  }
  session.ready = true
}

/** 刷新页面后恢复身份：有票就问一次“我是谁”。 */
export async function restore() {
  if (session.ready) return session.profile
  if (!auth.token()) {
    session.ready = true
    return null
  }
  try {
    applyProfile(await api.me())
  } catch {
    // 票失效时 http.ts 已经清票并跳登录页，这里不必再动作
    session.ready = true
  }
  return session.profile
}

export function signOut() {
  auth.clear()
  session.profile = null
  session.ready = false
  location.hash = '#/login'
}
