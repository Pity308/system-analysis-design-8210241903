const api = require('./utils/api.js')

App({
  globalData: {
    patient: null,
    bound: false
  },

  onLaunch: function () {
    // 首屏之前就把票和档案准备好，页面里就不用各自写一遍登录逻辑
    this.loginReady = api.ensureLogin()
      .then(function () { return api.me() })
      .then(function (r) {
        this.globalData.bound = !!(r && r.bound)
        this.globalData.patient = (r && r.patient) || null
        return r || { bound: false }
      }.bind(this))
      .catch(function () {
        return { bound: false, patient: null }
      })
  },

  /** 页面统一入口：等登录完成，拿当前档案。 */
  ready: function () {
    if (!this.loginReady) this.onLaunch()
    return this.loginReady
  },

  /** 绑定成功后刷新全局档案。 */
  setPatient: function (patient) {
    this.globalData.patient = patient || null
    this.globalData.bound = !!patient
  }
})
