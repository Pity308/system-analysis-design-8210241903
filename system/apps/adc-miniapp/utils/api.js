/**
 * 患者端接口封装。
 *
 * 三条约定，对应报告 6.12 的患者端鉴权约束：
 * 1) 票（token）只存在本地 storage，每个请求放 Authorization 头；
 * 2) 任何接口都不带 patientId —— 服务端用票里的 openid 换患者，前端无从冒充；
 * 3) 401 自动重新 wx.login 一次，不把“登录过期”这种噪音抛给患者看。
 */
const { BASE_URL, SLOT_MINUTES } = require('../config.js')

const TOKEN_KEY = 'adc_token'

function raw(path, method, data, token) {
  return new Promise(function (resolve, reject) {
    const header = { 'Content-Type': 'application/json' }
    if (token) header.Authorization = 'Bearer ' + token
    wx.request({
      url: BASE_URL + path,
      method: method,
      data: data,
      header: header,
      timeout: 15000,
      success: function (res) {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
          return
        }
        const body = res.data || {}
        const err = new Error(body.message || '请求失败（' + res.statusCode + '）')
        err.status = res.statusCode
        err.code = body.code || ''
        reject(err)
      },
      fail: function (e) {
        const err = new Error('网络异常，请检查网络后重试')
        err.status = 0
        err.raw = e && e.errMsg
        reject(err)
      }
    })
  })
}

/** wx.login 换票。返回后端给的 { token, patient }。 */
function login() {
  return new Promise(function (resolve, reject) {
    wx.login({
      success: function (r) {
        if (!r.code) {
          reject(new Error('微信登录没有返回 code，请重试'))
          return
        }
        raw('/api/wx/login', 'POST', { code: r.code }, '')
          .then(function (d) {
            if (d && d.token) wx.setStorageSync(TOKEN_KEY, d.token)
            resolve(d || {})
          })
          .catch(reject)
      },
      fail: function (e) {
        reject(new Error('微信授权失败：' + ((e && e.errMsg) || '')))
      }
    })
  })
}

/** 保证本地有票；没有就登录一次。 */
function ensureLogin() {
  const token = wx.getStorageSync(TOKEN_KEY)
  if (token) return Promise.resolve(token)
  return login().then(function (d) { return d.token })
}

function request(path, method, data, retried) {
  return ensureLogin().then(function (token) {
    return raw(path, method, data, token).catch(function (e) {
      if (e.status === 401 && !retried) {
        wx.removeStorageSync(TOKEN_KEY)
        return request(path, method, data, true)
      }
      throw e
    })
  })
}

module.exports = {
  ensureLogin: ensureLogin,

  /** 当前档案；bound=false 表示还没绑定。 */
  me: function () {
    return request('/api/wx/me', 'GET')
  },

  /** 建档 / 绑定既有档案。 */
  bind: function (profile) {
    return request('/api/wx/bind', 'POST', profile)
  },

  /** 参考数据：诊所、治疗项目。游客也能看，但统一走带票通道，少一条分支。 */
  clinics: function () {
    return request('/api/clinics', 'GET')
  },

  treatmentItems: function () {
    return request('/api/treatment-items', 'GET')
  },

  /**
   * 可约时段。
   * treatmentItemIds 用逗号拼成字符串：后端是 List<Integer>，逗号分隔是 Spring 的标准绑定方式，
   * 用数组序列化成 itemIds[0]=1 反而会因参数名带下标而绑不上。
   */
  availability: function (date, clinicId, itemIds) {
    let q = '/api/availability?date=' + date + '&minutes=' + SLOT_MINUTES
    if (clinicId) q += '&clinicId=' + clinicId
    if (itemIds && itemIds.length) q += '&treatmentItemIds=' + itemIds.join(',')
    return request(q, 'GET')
  },

  /** 自助预约。渠道与患者都由服务端定，这里不传。 */
  book: function (payload) {
    return request('/api/wx/appointments', 'POST', payload)
  },

  myAppointments: function () {
    return request('/api/wx/appointments', 'GET')
  },

  cancel: function (appointmentId, reason) {
    return request('/api/wx/appointments/' + appointmentId + '/cancel', 'POST',
      { reason: reason || '' })
  }
}
