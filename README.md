# 华电教务 App(NcepuJw)

一个非官方的华北电力大学教务系统 Android 客户端,通过逆向 `https://jwxt.ncepu.edu.cn/`(强智科技教务系统,老版 jsxsd 部署)的 Web 接口实现,使用原生 Kotlin + Jetpack Compose 开发。**登录、课表、成绩均已用真实账号端到端验证通过。**

> ⚠️ 仅供个人学习查分使用,请勿用于商业用途或高频请求。凭据只保存在本机 SharedPreferences。

## 功能

- **登录**:教务系统账号密码(与网页端一致),支持记住密码自动登录
- **课表**:按学年学期查询周课表网格,今天高亮,同课程同色
- **成绩**:按学期查询成绩列表,自动计算加权平均分、加权绩点、总学分
- **我的**:显示学号/姓名,退出登录
- **Material You 主题**:
  - **莫奈动态取色**:Android 12+ 根据系统壁纸生成全套配色(可关闭,回退华电蓝品牌色)
  - **深浅色**:跟随系统 / 强制浅色 / 强制深色,三档切换即时生效
  - **过渡动画**:登录↔主界面淡入缩放、底部 Tab 方向感知滑动、设置页导航转场
- **上课提醒**(设置页开启):
  - 按课表为每节课设置精确闹钟,提前 0/5/10/15/20 分钟(可调)发通知
  - 节次上课时间可在设置中编辑(默认按华电作息:1-2节 08:00、3-4节 10:00、5-6节 14:30、7-8节 16:30、9-10节 19:30)
  - 未来 3 天滚动窗口 + 每日 04:03 自动续排;开机自启重建提醒;未授予"闹钟和提醒"精确权限时自动降级为窗口闹钟

## 接口逆向分析(已用真实账号端到端验证)

> 华电部署的是强智**老版 jsxsd 风格**:业务接口全部带 `/jsxsd/` 前缀、以 `.do` 结尾、返回**服务端渲染 HTML**(非 JSON)、无 csrftoken 要求。

### 1. 登录流程(核心)

**第 1 步** — 登录页 `GET /`,领取 `JSESSIONID`。

**第 2 步** — 预检(取随机种子):

```
POST /Logon.do?method=logon&flag=sess
```

返回 `scode#sxh`:`#` 前是 40 位随机串,`#` 后是 20 位数字串(每位 1-4)。

**第 3 步** — 按登录页内嵌 JS 的 `login()` 算法编码:

```js
code = userAccount + "%%%" + userPassword
encoded = ""
for (i = 0; i < code.length; i++) {
    if (i < 20) {
        n = parseInt(sxh[i])
        encoded += code[i] + scode.substring(0, n)
        scode = scode.substring(n)
    } else { encoded += code.substring(i); break }
}
```

实现见 [`QiangzhiCrypto.kt`](app/src/main/java/com/ncepu/jw/data/QiangzhiCrypto.kt)(已与原 JS 做 1000 组随机输入一致性校验)。

**第 4 步** — 提交登录:

```
POST /Logon.do?method=logon
userAccount=<学号>&userPassword=<密码>&encoded=<编码结果>
```

- 成功:`302 → /jsxsd/xk/LoginToXk?method=jwxt&ticqzket=...` →(继续 302)→ `/jsxsd/framework/xsMain.jsp`
- **跳转链必须手动跟随到底,jsxsd 会话才算建立**(OkHttp 需 `followRedirects(false)` 手动跟跳)
- 失败:200 返回登录页 HTML(部分部署静默回页,无 alert)

### 2. 课表接口

```
GET /jsxsd/xskb/xskb_list.do?xnxq01id=2026-2027-1
```

- `xnxq01id` 是**唯一的学期参数**,格式 `学年-学期序号`(如 `2026-2027-1`);传新版强智的 `xnm/xqm` 会被**静默忽略**(返回默认学期)
- 返回 HTML,课表在 `<table id="kbtable">`,**转置结构**:
  - 表头行:星期一~星期日(th)
  - 数据行:行标签 th(`第一大节`…`第五大节`)+ 7 个 td(周一~日格子)
  - 格内 `<div class="kbcontent1">`,内容按 `<br>` 分行:`课程号 | 课程名 | 周次(如 2-9(周)) | 教室`;同一格多段用 `------` 分隔(单双周/多周段)
  - 大节 → 节次:第一大节=1-2节,第二大节=3-4节……

### 3. 成绩接口

```
GET  /jsxsd/kscj/cjcx_frm                    ← 必须先访问(注册会话状态)
POST /jsxsd/kscj/cjcx_list   {xnm=2025-2026, xqm=1}
```

- **跳过入口页直接 POST 会返回"出错了"页(853 字节,含 `chucuole.gif`)**
- 返回 HTML `<table id="dataList">`,一次返回**全部学期**成绩,App 按"开课学期"列(如 `2025-2026-1`)本地过滤
- 15 列:`序号|开课学期|课程编号|课程名称|成绩|成绩标识|学分|总学时|绩点|补重学期|考核方式|考试性质|课程属性|课程性质|通选课类别`(该部署绩点列为空,GPA 按加权平均分展示)

### 4. 其他

- 学籍卡片(可取姓名):`GET /jsxsd/grxx/xsxx`
- 会话失效表现:任何页面返回 853 字节错误页(含 `chucuole.gif` + `/jsxsd/xk/LoginToXk?method=exit`)→ 需重新登录
- **短时间高频登录会触发服务端限制**(新登录踢旧会话/出错页),App 正常使用(每天自动登录 1-2 次)无影响

## 构建与运行

```bash
# 需要 JDK 17 与 Android SDK(platforms;android-35, build-tools;35.0.0)
export JAVA_HOME=<jdk17>
export ANDROID_HOME=<sdk>
gradle assembleDebug
```

产物:`app/build/outputs/apk/debug/app-debug.apk`,直接安装到 Android 8.0+ 设备。

## 项目结构

```
app/src/main/java/com/ncepu/jw/
├── MainActivity.kt          # 入口 + AppViewModel(状态)+ 导航/动画 + 主题接线
├── data/
│   ├── QiangzhiCrypto.kt    # 强智登录加密算法(逆向自登录页 JS)
│   ├── JwClient.kt          # OkHttp 客户端:登录/课表/成绩 + 会话管理
│   ├── SettingsStore.kt     # 设置(主题/提醒/节次时间)+ 课表 JSON 缓存
│   └── Models.kt            # Semester / Course / Grade 数据模型
├── reminder/
│   ├── ReminderScheduler.kt # 上课提醒闹钟调度(3 天滚动窗口)
│   └── ReminderReceiver.kt  # 闹钟→通知 + 开机重建(BootReceiver)
└── ui/
    ├── LoginScreen.kt       # 登录页
    ├── ScheduleScreen.kt    # 周课表网格
    ├── GradeScreen.kt       # 成绩列表 + GPA 汇总
    ├── SettingsScreen.kt    # 设置页(外观/提醒)
    ├── ProfileScreen.kt     # 个人页
    └── theme/Theme.kt       # Material You 主题(莫奈动态取色 + 深浅色)
```

## 已知限制

- 华电部分接口路径/参数可能与强智通用版有差异,若课表/成绩为空,可抓包核对 `gnmkdm` 与接口路径后改 `JwClient.kt` 中的常量
- 课表未做按周过滤(整学期视图);成绩绩点取接口返回的 `jd` 字段
- 未实现统一身份认证(CAS)登录与验证码场景
