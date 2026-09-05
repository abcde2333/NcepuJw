package com.ncepu.jw

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ncepu.jw.data.Course
import com.ncepu.jw.data.Grade
import com.ncepu.jw.data.JwClient
import com.ncepu.jw.data.NavBarShape
import com.ncepu.jw.data.NavMaterial
import com.ncepu.jw.data.Semester
import com.ncepu.jw.data.SettingsStore
import com.ncepu.jw.data.ThemeMode
import com.ncepu.jw.reminder.ReminderScheduler
import com.ncepu.jw.ui.AppBottomBar
import com.ncepu.jw.ui.BackgroundCropScreen
import com.ncepu.jw.ui.ExamScreen
import com.ncepu.jw.ui.GradeScreen
import com.ncepu.jw.ui.LoginScreen
import com.ncepu.jw.ui.ProfileScreen
import com.ncepu.jw.ui.PyfaScreen
import com.ncepu.jw.ui.ScheduleScreen
import com.ncepu.jw.ui.SelectionScreen
import com.ncepu.jw.ui.SettingsScreen
import com.ncepu.jw.ui.applyBackgroundBlur
import com.ncepu.jw.ui.theme.NcepuTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

/** 外观状态(实时驱动 UI) */
data class Appearance(
    val navShape: NavBarShape,
    val navMaterial: NavMaterial,
    val bgHas: Boolean,
    val bgBlur: Float,
    val bgDim: Float,
    val weekStartMillis: Long,
    val bgVersion: Int,
)

class AppViewModel(app: android.app.Application) : AndroidViewModel(app) {
    val client = JwClient()
    val settings = SettingsStore(app)

    var account by mutableStateOf("")
    var password by mutableStateOf("")
    var name by mutableStateOf<String?>(null)

    var loginLoading by mutableStateOf(false)
    var loginError by mutableStateOf<String?>(null)
    var loggedIn by mutableStateOf(false)

    var semesters: List<Semester> = Semester.options(8)
    var schedSem by mutableStateOf(Semester.current())
    var schedLoading by mutableStateOf(false)
    var schedError by mutableStateOf<String?>(null)
    var courses by mutableStateOf<List<Course>>(emptyList())
    var schedMode by mutableStateOf("WEEK")      // WEEK=官方周课表 / ALL=学期全量
    var officialWeek by mutableStateOf(0)        // 官方当前周(教务系统计算)
    var selectedWeek by mutableStateOf(0)        // 当前查看的周(0=全部)

    var gradeSem by mutableStateOf(Semester.current())
    var gradeLoading by mutableStateOf(false)
    var gradeError by mutableStateOf<String?>(null)
    var grades by mutableStateOf<List<Grade>>(emptyList())

    var selLoading by mutableStateOf(false)
    var selError by mutableStateOf<String?>(null)
    var xkRounds by mutableStateOf<List<com.ncepu.jw.data.XkRound>>(emptyList())
    var selectedCourses by mutableStateOf<List<com.ncepu.jw.data.SelectedCourse>>(emptyList())
    var selectionLoaded by mutableStateOf(false)

    var pyfaLoading by mutableStateOf(false)
    var pyfaError by mutableStateOf<String?>(null)
    var pyfa by mutableStateOf<com.ncepu.jw.data.PyfaData?>(null)

    var examSem by mutableStateOf(Semester.current())
    var examLoading by mutableStateOf(false)
    var examError by mutableStateOf<String?>(null)
    var exams by mutableStateOf<List<com.ncepu.jw.data.Exam>>(emptyList())

    fun tryAutoLogin() {
        val creds = settings.loadCredentials()
        if (creds != null && !loggedIn) {
            account = creds.first; password = creds.second
            doLogin()
        }
    }

    fun doLogin() {
        loginLoading = true
        loginError = null
        viewModelScope.launch {
            val err = client.login(account.trim(), password)
            loginLoading = false
            if (err == null) {
                loggedIn = true
                name = client.studentName
                settings.storeCredentials(account.trim(), password)
                loadHomeWeek(null)
                loadGrades()
            } else {
                loginError = err
            }
        }
    }

    /** 官方"我的周课表":按周查询(周次由教务系统计算,零误差) */
    fun loadHomeWeek(week: Int?) {
        schedLoading = true
        schedError = null
        viewModelScope.launch {
            try {
                val rqText = week?.let { weekRqText(it) } ?: todayText()
                val hw = client.fetchHomeWeek(rqText)
                if (hw.week > 0) officialWeek = hw.week
                courses = hw.courses
                schedMode = "WEEK"
                selectedWeek = week ?: officialWeek
                settings.cacheCourses(courses)
                if (settings.reminderEnabled) {
                    ReminderScheduler.reschedule(getApplication())
                }
                schedError = if (courses.isEmpty()) "本周暂无课程" else null
            } catch (e: Exception) {
                schedError = e.message ?: "加载失败"
            } finally {
                schedLoading = false
            }
        }
    }

    /** 查询某周对应的日期(以官方当前周为锚,取该周周一) */
    private fun weekRqText(week: Int): String {
        val base = if (officialWeek > 0) officialWeek
        else SettingsStore.currentWeek(System.currentTimeMillis(), settings.weekStartMillis)
        val cal = Calendar.getInstance()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        cal.add(Calendar.DAY_OF_MONTH, -((dow + 5) % 7)) // 回到本周一
        cal.add(Calendar.DAY_OF_MONTH, (week - base) * 7)
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(cal.time)
    }

    private fun todayText(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    /** 学期全量课表("全部周次"模式) */
    fun loadScheduleFull() {
        schedLoading = true
        schedError = null
        viewModelScope.launch {
            try {
                courses = client.fetchCourses(schedSem)
                name = client.studentName ?: name
                settings.cacheCourses(courses)
                schedMode = "ALL"
                selectedWeek = 0
                schedError = if (courses.isEmpty()) "本学期暂无课表(接口返回为空)" else null
            } catch (e: Exception) {
                schedError = e.message ?: "加载失败"
            } finally {
                schedLoading = false
            }
        }
    }

    fun loadGrades() {
        gradeLoading = true
        gradeError = null
        viewModelScope.launch {
            try {
                val page = client.fetchGrades(gradeSem)
                grades = page.items.filter { it.term == gradeSem.key }
                name = client.studentName ?: name
                gradeError = null
            } catch (e: Exception) {
                grades = emptyList()
                gradeError = e.message ?: "加载失败"
            } finally {
                gradeLoading = false
            }
        }
    }

    fun loadSelection() {
        selLoading = true
        selError = null
        viewModelScope.launch {
            try {
                xkRounds = client.fetchXkRounds()
                selectedCourses = client.fetchSelectedCourses(gradeSem)
                selectionLoaded = true
                selError = null
            } catch (e: Exception) {
                selError = e.message ?: "加载失败"
            } finally {
                selLoading = false
            }
        }
    }

    fun loadPyfa() {
        if (pyfa != null || pyfaLoading) return
        pyfaLoading = true
        pyfaError = null
        viewModelScope.launch {
            try {
                pyfa = client.fetchPyfa()
            } catch (e: Exception) {
                pyfaError = e.message ?: "加载失败"
            } finally {
                pyfaLoading = false
            }
        }
    }

    fun loadExams() {
        examLoading = true
        examError = null
        viewModelScope.launch {
            try {
                exams = client.fetchExams(examSem)
                settings.cacheExams(exams)
                ReminderScheduler.reschedule(getApplication())
            } catch (e: Exception) {
                examError = e.message ?: "加载失败"
            } finally {
                examLoading = false
            }
        }
    }

    fun logout() {
        loggedIn = false
        account = ""; password = ""
        courses = emptyList(); grades = emptyList()
        xkRounds = emptyList(); selectedCourses = emptyList(); selectionLoaded = false
        pyfa = null; pyfaError = null
        exams = emptyList(); examError = null
        loginError = null
        ReminderScheduler.reschedule(getApplication())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // edge-to-edge:内容延伸到状态栏/导航条后面,背景与暗化全屏覆盖
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val settings = SettingsStore(this)

        setContent {
            var themeMode by mutableStateOf(settings.themeMode)
            var presetKey by mutableStateOf(settings.themePreset)
            var dynamicColor by mutableStateOf(settings.dynamicColor)
            var fontScale by mutableStateOf(settings.fontScale)
            var appearance by mutableStateOf(
                Appearance(
                    navShape = settings.navShape,
                    navMaterial = settings.navMaterial,
                    bgHas = settings.backgroundFile() != null,
                    bgBlur = settings.bgBlur,
                    bgDim = settings.bgDim,
                    weekStartMillis = settings.weekStartMillis,
                    bgVersion = 0,
                )
            )

            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(baseDensity.density, baseDensity.fontScale * fontScale)
            ) {
                NcepuTheme(
                    themeMode = themeMode,
                    presetKey = presetKey,
                    dynamicColor = dynamicColor,
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        App(
                            themeMode = themeMode,
                            presetKey = presetKey,
                            dynamicColor = dynamicColor,
                            fontScale = fontScale,
                            appearance = appearance,
                            onThemeModeChange = { settings.themeMode = it; themeMode = it },
                            onPresetChange = { settings.themePreset = it; presetKey = it },
                            onDynamicColorChange = { settings.dynamicColor = it; dynamicColor = it },
                            onFontScaleChange = { settings.fontScale = it; fontScale = it },
                            onAppearanceChange = { appearance = it },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun App(
        vm: AppViewModel = viewModel(),
        themeMode: ThemeMode,
        presetKey: String,
        dynamicColor: Boolean,
        fontScale: Float,
        appearance: Appearance,
        onThemeModeChange: (ThemeMode) -> Unit,
        onPresetChange: (String) -> Unit,
        onDynamicColorChange: (Boolean) -> Unit,
        onFontScaleChange: (Float) -> Unit,
        onAppearanceChange: (Appearance) -> Unit,
    ) {
        val ctx = LocalContext.current
        val navController = rememberNavController()

        // 状态栏图标颜色跟随主题
        val isDarkNow = themeMode == ThemeMode.DARK ||
            (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme())
        LaunchedEffect(isDarkNow) {
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !isDarkNow
        }

        LaunchedEffect(Unit) { vm.tryAutoLogin() }

        val notifPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { ReminderScheduler.reschedule(ctx) }

        val evalLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { vm.loadGrades() }

        val xkLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { vm.loadSelection() }

        var pendingCropUri by mutableStateOf<android.net.Uri?>(null)

        val bgPicker = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                pendingCropUri = uri
                navController.navigate("bgcrop")
            }
        }

        fun applyReminder(enabled: Boolean) {
            vm.settings.reminderEnabled = enabled
            if (enabled && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                ReminderScheduler.reschedule(ctx)
            }
        }

        AnimatedContent(
            targetState = vm.loggedIn,
            transitionSpec = {
                (fadeIn(tween(350)) + androidx.compose.animation.scaleIn(
                    initialScale = 0.92f, animationSpec = tween(350)))
                    .togetherWith(fadeOut(tween(250)))
            },
            label = "root",
        ) { logged ->
            if (!logged) {
                LoginScreen(
                    account = vm.account,
                    password = vm.password,
                    loading = vm.loginLoading,
                    error = vm.loginError,
                    onAccountChange = { vm.account = it },
                    onPasswordChange = { vm.password = it },
                    onLogin = { vm.doLogin() },
                )
            } else {
                NavHost(
                    navController = navController,
                    startDestination = "main",
                    enterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Start,
                            animationSpec = tween(320),
                            initialOffset = { it / 6 },
                        ) + fadeIn(tween(320))
                    },
                    exitTransition = { fadeOut(tween(220)) },
                    popEnterTransition = { fadeIn(tween(220)) },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.End,
                            animationSpec = tween(320),
                            targetOffset = { it / 6 },
                        ) + fadeOut(tween(320))
                    },
                ) {
                    composable("main") {
                        MainScaffold(
                            vm = vm,
                            appearance = appearance,
                            isDark = themeMode == ThemeMode.DARK ||
                                (themeMode == ThemeMode.SYSTEM && isSystemInDarkTheme()),
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenPyfa = {
                                vm.loadPyfa()
                                navController.navigate("pyfa")
                            },
                            onOpenExams = { navController.navigate("exams") },
                            onEvaluate = {
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                evalLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(
                                            WebViewActivity.EXTRA_URL,
                                            JwClient.DEFAULT_BASE + "/jsxsd/newxspj/zhxspj_list.do",
                                        )
                                        putExtra(WebViewActivity.EXTRA_TITLE, "教学评价")
                                    }
                                )
                            },
                            onEnterRound = { round ->
                                WebViewActivity.webSession = vm.client.cookieHeader()
                                xkLauncher.launch(
                                    Intent(ctx, WebViewActivity::class.java).apply {
                                        putExtra(
                                            WebViewActivity.EXTRA_URL,
                                            JwClient.DEFAULT_BASE + round.url,
                                        )
                                        putExtra(WebViewActivity.EXTRA_TITLE, round.name.ifBlank { "选课" })
                                    }
                                )
                            },
                        )
                    }
                    composable("pyfa") {
                        PyfaScreen(
                            loading = vm.pyfaLoading,
                            error = vm.pyfaError,
                            data = vm.pyfa,
                            onRetry = { vm.loadPyfa() },
                        )
                    }
                    composable("exams") {
                        ExamScreen(
                            semesters = vm.semesters,
                            selected = vm.examSem,
                            loading = vm.examLoading,
                            error = vm.examError,
                            exams = vm.exams,
                            onSemesterChange = { vm.examSem = it; vm.loadExams() },
                            onRetry = { vm.loadExams() },
                        )
                    }
                    composable("bgcrop") {
                        pendingCropUri?.let { uri ->
                            BackgroundCropScreen(
                                uri = uri,
                                onCancel = {
                                    pendingCropUri = null
                                    navController.popBackStack()
                                },
                                onDone = {
                                    pendingCropUri = null
                                    onAppearanceChange(
                                        appearance.copy(bgHas = true, bgVersion = appearance.bgVersion + 1)
                                    )
                                    navController.popBackStack()
                                },
                            )
                        }
                    }
                    composable("settings") {
                        SettingsScreen(
                            themeMode = themeMode,
                            presetKey = presetKey,
                            dynamicColor = dynamicColor,
                            fontScale = fontScale,
                            navShape = appearance.navShape,
                            navMaterial = appearance.navMaterial,
                            hasBackground = appearance.bgHas,
                            bgBlur = appearance.bgBlur,
                            bgDim = appearance.bgDim,
                            reminderEnabled = vm.settings.reminderEnabled,
                            examReminderEnabled = vm.settings.examReminderEnabled,
                            leadMinutes = vm.settings.leadMinutes,
                            sectionTimes = vm.settings.sectionTimes,
                            weekStartMillis = appearance.weekStartMillis,
                            exactAlarmGranted = isExactAlarmGranted(ctx),
                            onThemeModeChange = onThemeModeChange,
                            onPresetChange = onPresetChange,
                            onDynamicColorChange = onDynamicColorChange,
                            onFontScaleChange = onFontScaleChange,
                            onNavShapeChange = { s ->
                                vm.settings.navShape = s
                                onAppearanceChange(appearance.copy(navShape = s))
                            },
                            onNavMaterialChange = { m ->
                                vm.settings.navMaterial = m
                                onAppearanceChange(appearance.copy(navMaterial = m))
                            },
                            onPickBackground = { bgPicker.launch("image/*") },
                            onClearBackground = {
                                vm.settings.clearScheduleBackground()
                                onAppearanceChange(
                                    appearance.copy(bgHas = false, bgVersion = appearance.bgVersion + 1)
                                )
                            },
                            onBgBlurChange = { f ->
                                vm.settings.bgBlur = f
                                onAppearanceChange(appearance.copy(bgBlur = f))
                            },
                            onBgDimChange = { f ->
                                vm.settings.bgDim = f
                                onAppearanceChange(appearance.copy(bgDim = f))
                            },
                            onReminderToggle = { applyReminder(it) },
                            onExamReminderToggle = { enabled ->
                                vm.settings.examReminderEnabled = enabled
                                ReminderScheduler.reschedule(ctx)
                            },
                            onLeadChange = { min ->
                                vm.settings.leadMinutes = min
                                ReminderScheduler.reschedule(ctx)
                            },
                            onTimesChange = { times ->
                                vm.settings.sectionTimes = times
                                ReminderScheduler.reschedule(ctx)
                            },
                            onWeekStartChange = { millis ->
                                vm.settings.weekStartMillis = millis
                                onAppearanceChange(appearance.copy(weekStartMillis = millis))
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    private fun isExactAlarmGranted(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** 解析本地背景图(采样,避免大图 OOM) */
    private fun loadBgBitmap(file: java.io.File): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 480) sample *= 2
        val bmp = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return null
        bmp.asImageBitmap()
    }.getOrNull()

    @Composable
    private fun MainScaffold(
        vm: AppViewModel,
        appearance: Appearance,
        isDark: Boolean,
        onOpenSettings: () -> Unit,
        onOpenPyfa: () -> Unit,
        onOpenExams: () -> Unit,
        onEvaluate: () -> Unit,
        onEnterRound: (com.ncepu.jw.data.XkRound) -> Unit,
    ) {
        var tab by remember { mutableStateOf(0) }
        val ctx = LocalContext.current

        // 背景层:View 容器常驻渲染树(背景图 + 暗化一体),同时是液态玻璃底栏的折射源。
        // 暗化用 FrameLayout 前景实现,必然覆盖全容器(含状态栏/导航条区域)。
        val bgHost = remember { FrameLayout(ctx) }
        val bgImageView = remember { ImageView(ctx) }
        val dimDrawable = remember {
            android.graphics.drawable.ColorDrawable(
                androidx.compose.ui.graphics.Color.Black.copy(alpha = appearance.bgDim.coerceIn(0f, 0.85f)).toArgb()
            )
        }
        var showBg by remember(appearance.bgHas, appearance.bgVersion) {
            mutableStateOf(appearance.bgHas)
        }

        remember(showBg) {
            bgHost.removeAllViews()
            bgHost.foreground = if (showBg) dimDrawable else null
            if (showBg) {
                bgHost.addView(
                    bgImageView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
            true
        }
        val densityValue = LocalDensity.current.density
        // 背景图解码在 IO 线程,避免阻塞主线程
        LaunchedEffect(appearance.bgHas, appearance.bgVersion, appearance.bgBlur, densityValue) {
            if (!appearance.bgHas) {
                bgImageView.setImageDrawable(null)
                return@LaunchedEffect
            }
            val bmp = withContext(Dispatchers.IO) {
                vm.settings.backgroundFile()?.let { loadBgBitmap(it) }
            }
            bgImageView.setImageBitmap(bmp?.asAndroidBitmap())
            applyBackgroundBlur(bgImageView, appearance.bgBlur, densityValue)
        }
        LaunchedEffect(appearance.bgDim, appearance.bgVersion) {
            dimDrawable.alpha = (appearance.bgDim.coerceIn(0f, 0.85f) * 255).toInt()
        }

        Box(Modifier.fillMaxSize()) {
            // 背景容器(最底层;液态玻璃底栏折射它)
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { bgHost },
                modifier = Modifier.fillMaxSize(),
            )
            Scaffold(
                containerColor = if (showBg) Color.Transparent else MaterialTheme.colorScheme.background,
                bottomBar = {
                    AppBottomBar(
                        shape = appearance.navShape,
                        material = appearance.navMaterial,
                        tab = tab,
                        onSelect = { t ->
                            tab = t
                            when (t) {
                                0 -> if (vm.courses.isEmpty()) vm.loadHomeWeek(null)
                                1 -> if (vm.grades.isEmpty()) vm.loadGrades()
                                2 -> if (!vm.selectionLoaded) vm.loadSelection()
                            }
                        },
                        glassTarget = if (showBg) bgHost else null,
                        isDark = isDark,
                        bgBlurDp = appearance.bgBlur,
                    )
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            val dir = if (targetState > initialState)
                                AnimatedContentTransitionScope.SlideDirection.Start
                            else AnimatedContentTransitionScope.SlideDirection.End
                            (slideIntoContainer(dir, tween(280), initialOffset = { it / 12 })
                                + fadeIn(tween(280)))
                                .togetherWith(
                                    slideOutOfContainer(dir, tween(280), targetOffset = { -it / 12 })
                                        + fadeOut(tween(200))
                                )
                        },
                        label = "tab",
                    ) { t ->
                        when (t) {
                            0 -> ScheduleScreen(
                                loading = vm.schedLoading,
                                error = vm.schedError,
                                courses = vm.courses,
                                mode = vm.schedMode,
                                officialWeek = vm.officialWeek,
                                selectedWeek = vm.selectedWeek,
                                semesters = vm.semesters,
                                selected = vm.schedSem,
                                sectionTimes = vm.settings.sectionTimes,
                                bgEnabled = showBg,
                                onWeekChange = { vm.loadHomeWeek(it) },
                                onShowAll = { vm.loadScheduleFull() },
                                onSemesterChange = { vm.schedSem = it; vm.loadScheduleFull() },
                                onRetry = {
                                    if (vm.schedMode == "ALL") vm.loadScheduleFull()
                                    else vm.loadHomeWeek(vm.selectedWeek.takeIf { it > 0 })
                                },
                                onOpenExams = onOpenExams,
                            )
                            1 -> GradeScreen(
                                semesters = vm.semesters,
                                selected = vm.gradeSem,
                                loading = vm.gradeLoading,
                                error = vm.gradeError,
                                grades = vm.grades,
                                onSemesterChange = { vm.gradeSem = it; vm.loadGrades() },
                                onRetry = { vm.loadGrades() },
                                onEvaluate = onEvaluate,
                            )
                            2 -> SelectionScreen(
                                loading = vm.selLoading,
                                error = vm.selError,
                                rounds = vm.xkRounds,
                                courses = vm.selectedCourses,
                                onEnterRound = onEnterRound,
                                onRetry = { vm.loadSelection() },
                            )
                            else -> ProfileScreen(
                                account = vm.account,
                                name = vm.name,
                                onOpenSettings = onOpenSettings,
                                onOpenPyfa = onOpenPyfa,
                                onLogout = {
                                    vm.settings.clearCredentials()
                                    vm.logout()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
