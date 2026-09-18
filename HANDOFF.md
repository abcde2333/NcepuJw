# 交接文档 · 华电教务 App (NcepuJw)

> 面向接手的开发 agent。当前版本 **4.21 / versionCode 52**。桌面小部件整套为**未提交**新工作（见 §8）。

## 1. 项目概述
非官方 华北电力大学(NCEPU) 教务 Android 客户端，逆向 `https://jwxt.ncepu.edu.cn/`（强智老版 **jsxsd** 部署，服务端渲染 HTML）+ 统一身份认证(`ids.ncepu.edu.cn`)，并集成校园生活服务：慧生活798 直饮水、U净洗衣机/烘干机。含应用内更新、上课/考试提醒、桌面小部件、校外 WebVPN 隧道。

- 语言/UI：Kotlin + Jetpack Compose，Material You；`@Immutable` 数据模型。
- 包名 `com.ncepu.jw`；仓库 `github.com/abcde2333/NcepuJw`（Public）。
- 声明：仅个人学习；凭据只存本机 SharedPreferences；高频请求会触发风控。

## 2. 环境与构建
- 工作目录 `D:\zcodeworkspace\NcepuJwApp`（Windows + Git Bash）。
- JDK 17；`sdk.dir=D:/zcodeworkspace/android-sdk`（见 `local.properties`，不入库）。
- Gradle：wrapper 指向 9.4.1（官方源偶发超时；本机可用 `D:/MC/mcmodev/gradle-9.4.1/bin/gradle`，`--offline` 走缓存）。AGP 9.1.1，compileSdk 37/37.2，minSdk 26，targetSdk 35。
- 关键依赖：okhttp4、compose-bom 2024.09.03、material-kolor、Kyant0 backdrop/shapes(液态玻璃)、mlkit 扫码、camera、jxl(读 .xls 课表)、bcprov(SM2)、alipaysdk、**sh.calvin.reorderable:2.5.1**(饮水机拖动排序，按 Compose 1.7 编译)。
- 构建：`gradle assembleDebug assembleRelease`；release 走 R8(`isMinifyEnabled`+`shrinkResources`)。
- 真机：`adb` 在 `D:/zcodeworkspace/android-sdk/platform-tools/adb.exe`；当前手机 serial `NRYP9XDUMZGEGMAU`（另有 emulator `127.0.0.1:16416`，adb 命令须 `-s <serial>` 否则报多设备）。

## 3. 签名与发布流程（重要）
- 固定签名：CI 从 GitHub Secret `KEYSTORE_BASE64` 还原 `release.keystore`（别名/口令 android）。本地 `release.keystore` 已 gitignore。**应用内更新覆盖安装要求新旧包同签名**——只有 CI 产物与线上包同密钥；本地 debug 装的包可能与线上签名不一致导致无法覆盖更新。
- 发版步骤（已固化，照做即可）：
  1. `app/build.gradle.kts` bump `versionCode`(+1) / `versionName`。
  2. 更新 `RELEASE_NOTES.txt`（纯文本，App 内更新弹窗会展示，CI 写入 latest.json.notes）。
  3. 需要时更新 `README.md`。
  4. `git add -A && git commit`；**先 `git fetch origin main` + `git rebase origin/main`**（CI 每次发布会往 main 提交 `latest.json: vX`，本地会落后）。
  5. `git push origin main`；`git tag -a vX.Y -m "..."`；`git push origin vX.Y`。
  6. `.github/workflows/android.yml` 监听 `v*`：R8 构建 → 重命名 `NcepuJw-vX.Y.apk` → 计算 sha256 → **PUT** 写 `latest.json`(versionCode/versionName/apkUrl/apkHash/notes，notes 从 main 的 `RELEASE_NOTES.txt` curl 读取) → **purge jsDelivr** → 建 Release(`generate_release_notes:true`)。
  7. CI 后 `gh release edit vX.Y --notes-file <中文正文>` 覆盖英文自动生成说明；核验 `latest.json` 与 jsDelivr 已更新。
- 用 `gh` CLI（已登录）。`latest.json` 由 CI 写，勿手改（会与 CI 冲突/提前指向不存在 APK）。

## 4. 代码架构地图
- `MainActivity.kt`（~2100 行）：`AppViewModel(AndroidViewModel)` 持全部 UI 状态(`by mutableStateOf`) + 各业务方法；`MainActivity : ComponentActivity` 用 `setContent` + `NavHost`（路由 main/login/settings/washer/waterscan/exams/grades/pyfa/bgcrop 等）。`main` 内 `MainScaffold` 底部 tab：**0 课表 / 1 饮水 / 2 选课 / 3 我的**（`tab` 为 `rememberSaveable`）。深链：`MainActivity.EXTRA_TAB` → `vm.pendingTab` → MainScaffold `LaunchedEffect` 切 tab。
- `data/`：`JwClient`(教务+统一认证+WebVPN)、`QiangzhiCrypto`(登录交织码)、`UjingClient`(U净)、`IlifeClient`(慧生活)、`SettingsStore`(SharedPreferences "jw")、`Models`(Course/Grade/Semester/XkRound/parseWeeks)、`ScheduleXlsParser`。
- `ui/`：`ScheduleScreen`(含 `internal fun sectionSlots` 节次→时间)、`WasherScreen`、`WaterScreen`、`SelectionScreen`、`GradeScreen`、`ProfileScreen`、`SettingsScreen`、`LoginScreen`(教务密码/统一认证双模式+液态玻璃切换)、`BottomBar`(液态玻璃拖拽切页)、`glass/`。
- `reminder/`：`ReminderScheduler`(精确闹钟+每日04:03滚动+开机重建)、`ReminderReceiver`(`refresh`/`exam`/上课/`widget_tick` 分支)、`BootReceiver`。
- `update/`：`Updater`(镜像链+sha256+安装)、`WasherWatchService`(洗衣前台服务)、`WebViewActivity`(SSO/选课/教评网页)。
- `widget/`：`ScheduleWidgetProvider`(静态 RemoteViews,今天/明天固定行,已移除 RemoteViewsService 以抗 MIUI 冻结)、`WidgetTicker`(下课点刷新,假期跳过)。
- 节假日同步：`data/HolidayClient`(timor.tech 当年/次年法定假日)+ `SettingsStore.skippedDates/isHoliday`,`MainActivity.refreshHolidays()` 联网刷新课表后触发,`ReminderScheduler` 假期整天不排提醒。

## 5. 逆向协议要点
### 5.1 教务(强智 jsxsd)
- 登录：`GET /` → `POST /Logon.do?method=logon&flag=sess`(返回 `scode#sxh`) → `QiangzhiCrypto.encodeCredentials`(账号%%%密码 交织) → `POST /Logon.do?method=logon` → **手动跟 302 到 `/jsxsd/`** 才算成功。
- 课表：`GET /jsxsd/xskb/xskb_list.do?xnxq01id=<学年-学期序号>`(HTML `table#kbtable`，行=大节/列=星期，格内 `div.kbcontent`)；备用 `POST /jsxsd/xskb/xskb_print.do?xnxq01id=&zc=` 返回 **.xls(OLE2)**。
- 成绩：先 `GET /jsxsd/kscj/cjcx_frm` 注册会话，再 `POST /jsxsd/kscj/cjcx_list {xnm,xqm}`。考试：`/jsxsd/xsks/xsksap_query|list`。选课轮次：`/jsxsd/xsxk/xklc_list`（"进入选课" href 可能相对/被代理改写，用 `JwClient.campusUrlOf` 归一）。
- 会话失效：任意页返回 **853 字节含 `chucuole.gif`** 的错误页 → 需重登。

### 5.2 统一认证 (ids.ncepu.edu.cn，金智 authserver `cn.ssoedu.auth`)
- OAuth2 直登：`/authserver/oauth2/authorize?client_id=202508121120132909063&redirect_uri=http://jwxt.hcc.edu.cn/Logon.do?method=logonByHbdldx&response_type=code` → Set-Cookie `COOKIE_INFO`(含 flowKey `flow.xxxx`) → `/authserver/api/reset/rules` 取 SM2 公钥 → `info-query` → `username-password/login {flowKey,username,password=SM2}` → `666666`+`data.service` → 跟 302 到 jwxt `Logon.do?code=` 建立教务会话。
- **SM2**：BouncyCastle `SM2Engine(C1C3C2)`，明文 `toHalfWidth`，输出 `04||C1||C3||C2` 再 Base64（Python 侧 gmssl 是 C1C2C3，需重排为 `04+C1+C3+C2`）。
- **短信 MFA**：`username-password/login` 可能返回 `160001 {mfa:sms}` → `POST /authserver/sms/code {flowKey,username,captchaData:""}` 发码 → `POST /authserver/mfa/sms {flowKey,username,smsCode}`。校内直连与校外隧道都可能需要，App 内已内联处理(短信输入框)。

### 5.3 校外 WebVPN (myvpn.ncepu.edu.cn，深信服)
- 代理 URL：`https://myvpn.ncepu.edu.cn/https/<enc(host)><path>`；`enc(host)=hex(iv)+AES-CFB128(key,iv, host补'0'到16倍数)[:len(host)]`，`key=iv="wrdvpnisthebest!"`（参考 lcandy2/webvpn-converter，华电 host=myvpn.ncepu.edu.cn）。
- 认证=统一认证 CAS：`/login?service=myvpn/login?cas_login=true` → authserver 登录(含短信 MFA) → `ticket=ST-` → `wengine-vpn-token-login?token=` → 得 `wengine_vpn_ticket…` cookie；此后教务内网 cookie 由 Sangfor 服务端按 wengine 会话自动附带。
- App 实现：`WebvpnInterceptor` 开关下把 jwxt/ids/hcc 域请求改写为代理 URL(含 Referer)，`wFollow` 把 `jwxt.hcc.edu.cn`→`jwxt.ncepu.edu.cn` 归一；教务会话校验只认 `chucuole`/短页(勿用含 method=logon 的模糊启发式)。设置「校外模式」=惰性登录：开 App/有缓存课表不自动登录，仅真正拉数据时 `webvpnLogin`(按需弹短信码)。

### 5.4 U净 (phoenix.ujing.online/api/v1，洗烘同业务线 x-app-code BA)
- 扫码 `POST devices/scanWasherCode {qrCode:<二维码原文>}` → `data.result{deviceId,deviceTypeId,createOrderEnabled,reason,status}`(**status 1=运行中/2=故障/8=离线**；`UjingClient.scanBadge` 映射空闲/使用中/故障/离线)。
- 套餐 `GET app/washer/devices/program/info?deviceId=` → `deviceWashModel[]{workModelId,workModelName,basePrice(分),time(分)}` + 加购 `additionDevices`/`additionParams`(温度 washTemperatureId、自洁 selfCleanId 等，按接口动态)。
- 下单 `POST orders/create {type(洗1/烘2),deviceTypeId,deviceId,deviceWashModelId,storeId,加购key...,dryTime?(分计时=分钟×10)}`；支付 `GET payment/arguments?channel=alipay` → `payInfo.orderInfo` → Alipay `PayTask.payV2`；订单 `orders/{id}/detail`；控制 `orders/{id}/control/start|stop`(**受理码可能 code=1703+data.errorCode=0**)。
- 订单状态：10待支付/20已支付待启动/22自洁启动中/30自洁中/**35自洁完成(需5分钟内启动)**/40运行中/50完成/60取消。`payPrice` 单位是**元**。
- 免扫码：`SavedWasher{did,name(设备号),deviceTypeId,storeId,status,note(备注),dryer,qr(原始二维码)}`；状态监测用 `scanWasherCode(存下的 qr)` 重扫（设备号当 qrCode 拿不到 createOrderEnabled，必须原始二维码）。

### 5.5 慧生活798 直饮水 (i.ilife798.com/api/v1)
- `ApplicationType 1,5`=账号(图形码/短信/登录→`data.al.token`)，`1,1`=设备；`ui/app/master` favos(online/offline 仅联网状态)；**真实忙闲** `ui/app/dev/status?did=&more=true` → `data.device.gene.status`(99=空闲，其它=出水中)+`gene.out`(升)。`dev/start|end`。拖动排序 `waterOrder` 持久化。

## 6. 关键约束/坑（务必知悉）
- **高频登录触发风控**：教务/统一认证短时间多次失败登录 → 数十分钟锁号(853 错误页/180028)。脚本/测试慎，单次为宜。
- **MIUI RemoteViews 白名单**：小部件布局**不能用裸 `<View>`**（`Class not allowed to be inflated android.view.View`），分隔线用 `TextView`+背景色替代。
- **集合小部件只能有一个可滚动 AdapterView**：无法左右两列各自独立滚动；当前=两列并排、整体上下滑。行点击要 `setPendingIntentTemplate`(provider，MUTABLE)+`setOnClickFillInIntent`(每行)。
- **reorderable 版本**：必须用按 Compose 1.7 编译的 `2.5.1`（2.3.2 是按 1.6.11 编译，与本项目 Compose 1.7.0 二进制不兼容，进饮水页会崩）。
- **jsDelivr 缓存**：`latest.json` 走 jsDelivr 会缓存旧版导致"检查更新→已是最新"；`Updater.fetchLatest` 已改为并行查所有镜像取 versionCode 最大 + `?z=` cache-buster；CI 每次发布 purge jsDelivr。镜像：jsDelivr/fastgit/ghfast(常挂)/gh-proxy/raw。
- **SM2 密文格式**：C1C3C2 + `04` 前缀 + Base64；错则服务器 `invalid cipher text`（非密码错，不计锁号）。
- 教务密码 ≠ 统一认证密码，**分开存储**（KEY_ACCOUNT vs KEY_SSO_*）。

## 7. 约定/工作方式
- 中文注释与提交信息；**不每次提交**（用户会说"提交/发布"再动）。
- 大特性先**独立脚本验证**再进 App（`D:\zcodeworkspace\vpntest\`：`vpn_login.py`/`vpn_flow.py`/`probe_xk.py` 是 WebVPN/教务逆向验证脚本）。
- 不碰生产高频；不改后端。App 改动默认不自动发版。
- 发版前本地 `assembleDebug assembleRelease` 通过；CI 会再 R8。

## 8. 当前未提交工作（桌面小部件，需真机验证后再提交发版）
未提交文件：`AndroidManifest.xml`、`MainActivity.kt`、`data/SettingsStore.kt`、`reminder/ReminderReceiver.kt`、`ui/SettingsScreen.kt`、`res/values/strings.xml`；新增 `widget/`(Provider/Service/WidgetTicker)、`res/layout/widget_*.xml`、`res/drawable/widget_bg_*.xml`+`widget_chip_bg.xml`、`res/xml/widget_schedule_info.xml`。

小部件「今日课表」特性：
- RemoteViews 集合控件(ListView，可上下滑)，**左右两列**：左"今天"(过滤掉已上完的课)、右"明天"，各列课程 名称/教室·老师/时间，无课"没有课啦"。
- 头部：学期缩写(非年级) + `M.d 第N周 周X` + **饮水快捷按钮**(深链 tab1)。点课程行/卡面→打开 App 课表。
- 三档样式(深/浅/半透明)，设置「外观→小部件样式」`SingleChoiceSegmentedButtonRow` 全局选，存 `SettingsStore.widgetStyle`，VM `widgetStyle` 可观察即时生效。
- `WidgetTicker`：AlarmManager 定到"下一节课下课点"广播刷新小部件(隐藏已上完课)，自我续接；开机/更新时 arm。
- 已构建并 `adb install` 到手机；**尚未 commit / 未发版**。

## 9. 待办
- 真机验证小部件：两列显示、今天过课隐藏、点击跳转、饮水按钮、三档样式、MIUI 无崩溃。
- 烘干机 `type=2/dryTime/时长档位` 仍**待真实烘干机验证**（按逆向推断）。
- 校外 WebVPN 的 WebView(选课提交/教评) cookie 跨 context 共享需真机确认。
- 验证通过后：bump 53/4.22 → RELEASE_NOTES/README → 提交 → tag → CI 发布 → 中文 Release 正文。
