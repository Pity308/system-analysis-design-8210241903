/**
 * 小程序运行时配置。
 *
 * 开发：微信开发者工具 → 详情 → 本地设置 → 勾选“不校验合法域名”，
 *       模拟器里 BASE_URL 指向本机 127.0.0.1:8080 即可。
 * 体验版/正式版：手机访问不到 127.0.0.1，必须换成公网地址。
 *
 * 所以这里分两套：
 *   - 工具里（envVersion = develop）永远用 DEV，改后端不用管这里；
 *   - 体验版（trial）与正式版（release）用 PROD，只要把 PROD 填成服务器地址。
 *   - PROD 留空时退回 DEV，保证现在不填也能跑，不会因为忘配置而白屏。
 *
 * 上线还差一步：把后端 adc.wx.app-id / app-secret 换成真实值，
 * 否则登录走的是开发模式派生 openid（同一个人换手机就认不出来了）。
 */

/** 本机后端。模拟器可用；真机预览不行。 */
const BASE_URL_DEV = 'http://127.0.0.1:8080'

/**
 * 体验版 / 正式版用的地址。两种填法都行：
 *   有备案域名： 'https://adc.example.com'      ← 推荐，微信要求 https
 *   只有公网 IP：'http://1.2.3.4:8080'          ← 仅课程演示，正式发布必须换成 https 域名
 */
const BASE_URL_PROD = ''

function resolveBaseUrl() {
  if (!BASE_URL_PROD) return BASE_URL_DEV
  try {
    // develop 只在开发者工具里出现；trial 是体验版，release 是正式版
    const env = wx.getAccountInfoSync().miniProgram.envVersion
    return env === 'develop' ? BASE_URL_DEV : BASE_URL_PROD
  } catch (e) {
    // 老基础库没有 getAccountInfoSync，按开发态处理，避免直接崩
    return BASE_URL_DEV
  }
}

module.exports = {
  BASE_URL: resolveBaseUrl(),

  /** 连续预约天数：与后端 90 天上限相比，小程序只放最近两周，选起来不累。 */
  DAYS_AHEAD: 14,

  /** 每个时段的分钟数，与 UC-02 规则 2 一致。 */
  SLOT_MINUTES: 30,
}
