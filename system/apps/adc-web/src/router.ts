import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'
import { session, restore, workspacesOf } from './stores/session'
import { auth } from './api/http'

/**
 * 路由即“工作台 → 业务域 → 窗体”的两级导航（报告 6.13 图 14）。
 * 用 hash 模式：员工端最终由 nginx 托管静态文件，hash 不需要服务端 history 回退配置，
 * 少一个上线时的坑。
 *
 * 守卫只做入口处的第一道闸：没票去登录页，票的角色进不了这个工作台就送回自己的首页。
 * 真正的权限判定在服务端，绕过这里改 hash 也调不通接口。
 */
const routes: RouteRecordRaw[] = [
  { path: '/login', component: () => import('./views/LoginView.vue'), meta: { public: true, title: '登录' } },

  { path: '/', redirect: '/reception/patient' },

  // 前台收银台
  { path: '/reception/patient', component: () => import('./views/PatientForm.vue'), meta: { ws: 'reception', title: '患者建档' } },
  { path: '/reception/appointment', component: () => import('./views/AppointmentForm.vue'), meta: { ws: 'reception', title: '预约登记' } },
  { path: '/reception/arrival', component: () => import('./views/ArrivalDesk.vue'), meta: { ws: 'reception', title: '到诊登记' } },
  { path: '/reception/statement', component: () => import('./views/StatementDesk.vue'), meta: { ws: 'reception', title: '结算与收款' } },

  // 牙医工作台
  { path: '/dentist/visit', component: () => import('./views/VisitDesk.vue'), meta: { ws: 'dentist', title: '到诊与治疗' } },

  // 总部工作台
  { path: '/hq/clinic', component: () => import('./views/ClinicAdmin.vue'), meta: { ws: 'hq', title: '诊所信息' } },
  { path: '/hq/qualification', component: () => import('./views/QualificationAdmin.vue'), meta: { ws: 'hq', title: '资质维护' } },
  { path: '/hq/quarter', component: () => import('./views/QuarterReport.vue'), meta: { ws: 'hq', title: '季度业绩' } },
  { path: '/hq/operation', component: () => import('./views/OperationReport.vue'), meta: { ws: 'hq', title: '经营报表' } },
]

export const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

/** 某角色进入系统的落地页。 */
export function homeOf(role?: string): string {
  const ws = workspacesOf(role)[0]
  switch (ws) {
    case 'dentist':
      return '/dentist/visit'
    case 'hq':
      return '/hq/quarter'
    case 'reception':
      return '/reception/patient'
    default:
      return '/login'
  }
}

router.beforeEach(async (to) => {
  if (to.meta.public) {
    // 已登录还去登录页，直接送回自己的首页
    return auth.token() && session.profile ? homeOf(session.profile.roleCode) : true
  }

  await restore()

  if (!auth.token() || !session.profile) {
    return { path: '/login', query: { next: to.fullPath } }
  }

  const ws = to.meta.ws as string | undefined
  if (ws && !workspacesOf(session.profile.roleCode).includes(ws as any)) {
    return homeOf(session.profile.roleCode)
  }

  session.workspace = (ws as any) || session.workspace
  return true
})
