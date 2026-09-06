# 华电教务 App(NcepuJw)

一个非官方的华北电力大学教务系统 Android 客户端,通过逆向 `https://jwxt.ncepu.edu.cn/`(强智科技教务系统,老版 jsxsd 部署)的 Web 接口实现,并集成了校园生活服务(慧生活798 直饮水、U净洗衣机)。原生 Kotlin + Jetpack Compose,Material You 风格。

> ⚠️ 仅供个人学习使用,请勿用于商业用途或高频请求。所有凭据只保存在本机 SharedPreferences,不上传任何服务器。本项目与华北电力大学及上述服务商均无关联。

## 功能

### 教务
- **登录**:教务系统账号密码(与网页端一致),逆向了登录页 JS 的字符交织加密;支持记住密码自动登录;登录入口在「我的」页,可跳过(不影响生活服务)
- **课表**:全量抓取 `xskb_list.do` + 本地按周过滤,HorizontalPager 无缝左右滑周;表头显示日期(以官方当前周锚定);上课地点、大节时间;同课程同色;XLS 导出接口(`xskb_print.do`)解析作为备用数据源(同课多段自动合并)
- **成绩**:按学期查询,自动计算加权平均分、绩点、总学分(首次查看前需在网页端/WebView 完成评教)
- **考试安排**:按学期查询 + 考试开始前通知提醒
- **培养方案**、**选课中心**(含轮次开始/截止时间显示,选课操作跳 WebView 复用会话)

### 生活服务
- **饮水机(慧生活798)**:手机号 + 图形验证码 + 短信验证码登录(验证码发送 60s 冷却);设备列表、一键开关水;扫码或手动输入设备编号添加
- **U净洗衣**:手机号 + 短信验证码登录(60s 冷却);扫码识别洗衣机(原始二维码内容直传 `scanWasherCode`)、拉取洗涤套餐(价格/时长)、创建订单、拉起支付宝支付、订单状态轮询、启动/停止洗衣;已扫设备本地保存,下次免扫码
- 相机扫码基于 CameraX + ML Kit,自动从二维码/链接中提取设备编号

### 外观与体验
- **Material You 莫奈动态取色**(Android 12+,可关闭回退预设主题色);多套预设配色
- 深浅色三档(跟随系统/浅色/深色),切换带过渡动画
- **底栏形态可选**:标准/悬浮,材质支持实色/**液态玻璃**(qmdeve/AndroidLiquidGlass)/**高斯模糊**(实时背景折射)
- **自定义课表背景图**(拖动 + 双指缩放裁剪),高斯模糊强度、暗化程度可调(暗化覆盖全屏含顶栏底栏)
- 文本大小、节次上课时间自定义;周起始日设置

### 提醒
- **上课提醒**:按课表精确闹钟,提前 0/5/10/15/20 分钟可调;节次时间跟随设置;3 天滚动窗口 + 每日 04:03 续排;开机自启重建;无精确闹钟权限时自动降级窗口闹钟
- **考试提醒**:独立开关

## 接口逆向分析

> 华电部署的是强智**老版 jsxsd 风格**:业务接口全部带 `/jsxsd/` 前缀、以 `.do` 结尾、返回**服务端渲染 HTML**(非 JSON)。

### 1. 教务登录

1. `GET /` 领取 `JSESSIONID`
2. `POST /Logon.do?method=logon&flag=sess` 预检,返回 `scode#sxh`(`#` 前 40 位随机串,后 20 位数字串,每位 1-4)
3. 按登录页内嵌 JS 的 `login()` 编码:

```js
code = userAccount + "%%%" + userPassword
for (i = 0; i < code.length; i++) {
    if (i < 20) {
        n = parseInt(sxh[i])
        encoded += code[i] + scode.substring(0, n)
        scode = scode.substring(n)
    } else { encoded += code.substring(i); break }
}
```

   实现见 [`QiangzhiCrypto.kt`](app/src/main/java/com/ncepu/jw/data/QiangzhiCrypto.kt)(与原 JS 做过 1000 组随机输入一致性校验)
4. `POST /Logon.do?method=logon`(userAccount/userPassword/encoded),**302 跳转链必须手动跟到底**(`LoginToXk?method=jwxt` → `xsMain.jsp`),jsxsd 会话才算建立

### 2. 课表

```
GET /jsxsd/xskb/xskb_list.do?xnxq01id=2026-2027-1
```

- `xnxq01id` 是唯一学期参数(`学年-学期序号`);传新版强智的 `xnm/xqm` 会被静默忽略
- 返回 HTML `<table id="kbtable">`,**转置结构**:行=大节,列=星期;格内 `div.kbcontent` 多段以 `------` 分隔
- 备用:`POST /jsxsd/xskb/xskb_print.do?xnxq01id=&zc=` 返回 JXL 可解析的 .xls(7 行一段:课程号/名/分组/教师/周次/教室/节次)
- 首页周课表(官方按周过滤):`POST /jsxsd/framework/main_index_loadkb.jsp {rq: 日期}`

### 3. 成绩 / 考试

```
GET  /jsxsd/kscj/cjcx_frm            ← 必须先访问(注册会话状态),否则返回 853 字节错误页
POST /jsxsd/kscj/cjcx_list  {xnm=2025-2026, xqm=1}   → <table id="dataList">(15 列)

GET  /jsxsd/xsks/xsksap_query?xnxqid=<学期>  → POST /jsxsd/xsks/xsksap_list
```

### 4. 饮水机(慧生活798)

网关 `https://i.ilife798.com/api/v1`,头 `ApplicationType`:`1,5`=账户服务(图形验证码/短信),`1,1`=设备控制(登录/设备/开关水)。登录后 token 在 `data.al.token`。

```
GET  /ui/verify/code?key=<随机串>        # 图形验证码
GET  /ui/app/sms?...                    # 发送短信(需图形验证码)
POST /ui/app/login                      # → data.al.token
GET  /ui/app/master                     # → data.favos[](收藏设备)
GET  /dev/start|end?did=<设备号>&upgrade=true&ptype=91&rcp=false
```

协议参考 [nocookies111/life-798](https://github.com/nocookies111/life-798),实现见 [`IlifeClient.kt`](app/src/main/java/com/ncepu/jw/data/IlifeClient.kt)。

### 5. 洗衣机(U净)

网关 `https://phoenix.ujing.online/api/v1/`,头 `x-app-code`(`ZA`=账号 / `BA`=业务)、`x-app-version` 等;响应壳 `{code, message, data}`,成功时**调用方拿到的是剥壳后的 data 层**。

```
GET  captcha?mobile=&type=1&sessionId/token/sig=AFS_SWITCH_OFF
POST login {mobile, captcha}                        # → data.token
POST devices/scanWasherCode {qrCode: <二维码原始内容>} # → data.result{deviceId, deviceTypeId, createOrderEnabled, reason}
GET  app/washer/devices/program/info?deviceId=      # → data.storeId, data.deviceWashModel[]{workModelId, workModelName, basePrice(分), time(分)}
POST orders/create {type:1, deviceTypeId, deviceId, deviceWashModelId, storeId, washTemperatureId:1}
GET  payment/arguments?channel=alipay&orderId=      # → data.payInfo.orderInfo(拉起支付宝)
GET  orders/{id}/detail                             # 状态/剩余时间
GET  orders/{id}/control/start|stop
```

注意:`scanWasherCode` 必须传**二维码原始内容**(服务端自行解析,勿预提取设备号);下单所需的 `deviceTypeId` 来自扫码结果而非套餐接口。协议参考 [amamiyakazuki/FlandreSY](https://github.com/amamiyakazuki/FlandreSY),实现见 [`UjingClient.kt`](app/src/main/java/com/ncepu/jw/data/UjingClient.kt)。

### 6. 其他调研结论

- 会话失效表现:任何页面返回 853 字节错误页(含 `chucuole.gif`)→ 需重新登录
- **短时间高频登录会触发服务端持续限制**(数十分钟内所有请求返回错误页),正常使用(每天自动登录 1-2 次)无影响,自动化测试需注意频率
- WebVPN:泛解析存在但网关从公网不可达、门户无 Web 转发资源 → 纯客户端 WebVPN 通道不可行;校外使用请装官方 EasyConnect(服务器 ycbg.ncepu.edu.cn)

## 构建与运行

```bash
# 需要 JDK 17 与 Android SDK(compileSdk 37.2,构建工具随 gradle 自动选择)
export ANDROID_HOME=<sdk路径>    # 或在 local.properties 写 sdk.dir(local.properties 不入库)
gradle assembleDebug
```

产物:`app/build/outputs/apk/debug/app-debug.apk`,Android 8.0+ 可安装。扫码、通知、精确闹钟需在系统设置里授予对应权限。

## 项目结构

```
app/src/main/java/com/ncepu/jw/
├── MainActivity.kt            # 入口 + AppViewModel(全部状态)+ 导航/动画 + 生活服务业务流
├── WebViewActivity.kt         # 选课/评教 WebView(复用教务会话)
├── data/
│   ├── QiangzhiCrypto.kt      # 强智登录加密算法(逆向自登录页 JS)
│   ├── JwClient.kt            # 教务客户端:登录/课表/成绩/考试/选课/培养方案
│   ├── ScheduleXlsParser.kt   # 课表 XLS 导出解析(JXL)
│   ├── IlifeClient.kt         # 慧生活798 饮水机客户端
│   ├── UjingClient.kt         # U净洗衣机客户端
│   ├── SettingsStore.kt       # 设置 + 课表缓存 + 设备列表持久化
│   └── Models.kt              # Semester / Course / Grade / Exam 数据模型
├── reminder/
│   ├── ReminderScheduler.kt   # 上课/考试提醒闹钟调度
│   └── ReminderReceiver.kt    # 闹钟→通知 + 开机重建
└── ui/
    ├── ScheduleScreen.kt      # 周课表(HorizontalPager 无缝滑周)
    ├── GradeScreen.kt         # 成绩 + GPA 汇总
    ├── ExamScreen.kt          # 考试安排
    ├── SelectionScreen.kt     # 选课中心
    ├── PyfaScreen.kt          # 培养方案
    ├── WaterScreen.kt         # 饮水机(登录/设备/开关水/添加)
    ├── WaterScanScreen.kt     # 相机扫码(CameraX + ML Kit)
    ├── WasherScreen.kt        # U净洗衣(登录/识别/下单/支付/启停)
    ├── BackgroundCropScreen.kt# 背景图裁剪
    ├── BottomBar.kt           # 底栏(标准/悬浮 × 实色/液态玻璃/高斯模糊)
    ├── SettingsScreen.kt / ProfileScreen.kt / LoginScreen.kt
    └── theme/Theme.kt         # Material You 主题(莫奈取色 + 深浅色)
```

## 已知限制

- 饮水机/洗衣机的余额、洗涤价格以官方 App 为准;U净支付参数由官方接口生成,依赖支付宝
- 强智各校部署有差异,若课表/成绩为空,抓包核对接口路径后改 `JwClient.kt` 常量
- 未实现统一身份认证(CAS)登录;教务系统为老版部署,若学校升级接口会失效
- 洗衣机扫码支持 QR 码与 DataMatrix;个别厂商私有码制可能识别不了

## 致谢

- [nocookies111/life-798](https://github.com/nocookies111/life-798) — 慧生活798 协议参考
- [amamiyakazuki/FlandreSY](https://github.com/amamiyakazuki/FlandreSY) — U净协议参考
- [YiQiuYes/schedule](https://github.com/YiQiuYes/schedule) — 强智课表接口思路参考
- [QmDeve/AndroidLiquidGlassView](https://github.com/QmDeve/AndroidLiquidGlassView)、[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) — 液态玻璃实现
