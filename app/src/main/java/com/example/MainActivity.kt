package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SimCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.PrepaidRecharge
import com.example.domain.UssdParser
import com.example.notifications.BalanceReadingReminderWorker
import com.example.notifications.PrepaidExpiryWorker
import com.example.notifications.RechargeReminderWorker
import com.example.data.local.AppDatabase
import com.example.data.model.DataBalanceLogEntity
import com.example.data.model.DataPlanEntity
import com.example.data.repository.DataPlanRepository
import com.example.ui.components.AddBalanceLogDialog
import com.example.ui.components.AdjustCycleDaysDialog
import com.example.ui.components.ConfigurePackageDialog
import com.example.ui.components.NewPrepaidPurchaseDialog
import com.example.ui.components.UssdReadingDialog
import com.example.ui.components.rememberUssdConsultation
import com.example.ui.screens.DataForecastScreen
import com.example.ui.screens.DataLogsScreen
import com.example.ui.screens.DataSettingsScreen
import com.example.ui.screens.Prepaid35LineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DataForecastViewModel
import com.example.updates.UpdateCheckWorker
import com.example.updates.UpdateInstaller
import com.example.updates.UpdateManager
import com.example.updates.UpdateManagerProvider
import com.example.ussd.SimInfoProvider
import com.example.ussd.UssdSystemApi
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = DataPlanRepository(database.dataDao())
        val viewModelFactory = DataForecastViewModel.provideFactory(repository)

        // Daily background checks: prepaid deadline + missing balance reading
        // + prepaid recharge date (09:00 local time).
        PrepaidExpiryWorker.schedule(applicationContext)
        BalanceReadingReminderWorker.schedule(applicationContext)
        RechargeReminderWorker.schedule(applicationContext)

        val updateManager = UpdateManagerProvider.get(applicationContext)
        UpdateCheckWorker.schedule(applicationContext)

        setContent {
            MyApplicationTheme {
                val viewModel: DataForecastViewModel = viewModel(factory = viewModelFactory)
                RequestNotificationPermissionIfNeeded()
                MainDataContent(viewModel = viewModel, updateManager = updateManager)
            }
        }
    }
}

/** Asks for POST_NOTIFICATIONS once on Android 13+ (expiry + reading reminders). */
@Composable
fun RequestNotificationPermissionIfNeeded() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** True when [logs] already contains a reading for [lineId] in the local day. */
private fun hasReadingToday(
    logs: List<DataBalanceLogEntity>,
    lineId: Int,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val startOfToday = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return logs.any { it.planId == lineId && it.timestamp >= startOfToday }
}

@Composable
fun MainDataContent(viewModel: DataForecastViewModel, updateManager: UpdateManager) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateManager.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingInstall by remember { mutableStateOf(false) }

    // Android only lets a sideloaded app install through its own confirmation
    // screen. We keep everything else invisible and remember the install
    // request while the user grants "install unknown apps" in system settings.
    fun tryInstallUpdate() {
        val path = updateState.downloadedPath ?: return
        val apkFile = File(path)
        if (!apkFile.isFile) {
            updateManager.clearDownload()
            return
        }
        if (UpdateInstaller.canRequestInstall(context)) {
            UpdateInstaller.install(context, apkFile)
        } else {
            pendingInstall = true
            context.startActivity(UpdateInstaller.manageUnknownSourcesIntent(context))
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingInstall &&
                UpdateInstaller.canRequestInstall(context)
            ) {
                pendingInstall = false
                updateState.downloadedPath?.let { path ->
                    val apkFile = File(path)
                    if (apkFile.isFile) UpdateInstaller.install(context, apkFile)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var targetLineId by rememberSaveable { mutableIntStateOf(1) }

    var showConfigurePackageDialog by remember { mutableStateOf(false) }
    var showAdjustDaysDialog by remember { mutableStateOf(false) }
    var showAddLogDialog by remember { mutableStateOf(false) }
    var showNewPurchaseDialog by remember { mutableStateOf(false) }
    var showUssdReadingDialog by remember { mutableStateOf(false) }
    var autoReadAttempted by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(autoReadAttempted) {
        if (autoReadAttempted) return@LaunchedEffect
        // Espera breve para que Room emita planes y lecturas iniciales.
        kotlinx.coroutines.delay(1200)
        autoReadAttempted = true

        suspend fun waitUntilIdle(maxMs: Long = 25_000L) {
            val start = System.currentTimeMillis()
            while (viewModel.uiState.value.consultingLineId != null &&
                System.currentTimeMillis() - start < maxMs
            ) {
                kotlinx.coroutines.delay(300)
            }
        }

        fun trySilentAutoRead(
            current: com.example.ui.viewmodel.DataForecastUiState,
            lineId: Int,
            ussdCode: String,
            selectedSimSlot: Int?
        ): Boolean {
            if (hasReadingToday(current.logs, lineId)) return false
            if (ussdCode.isBlank()) return false
            if (viewModel.uiState.value.consultingLineId != null) return false
            if (!UssdSystemApi.isAvailable(context)) return false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false

            val subscriptionId = if (selectedSimSlot != null) {
                if (!SimInfoProvider.hasPermission(context)) return false
                SimInfoProvider.subscriptionIdForSlot(context, selectedSimSlot) ?: return false
            } else {
                null
            }

            val sessionId = viewModel.startUssdConsultation(lineId, armAccessibility = false)
            val started = UssdSystemApi.request(context, ussdCode, subscriptionId) { result ->
                when (result) {
                    is UssdSystemApi.Result.Success -> {
                        val text = result.response
                        val hasReadableData =
                            UssdParser.parseDataBalanceMB(text) != null ||
                                (lineId == 2 && UssdParser.parseDaysRemaining(text) != null)
                        if (hasReadableData) {
                            viewModel.applyUssdReading(lineId, text, sessionId)
                        } else {
                            viewModel.cancelUssdConsultation(lineId, sessionId)
                        }
                    }
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> viewModel.cancelUssdConsultation(lineId, sessionId)
                }
            }
            if (!started) {
                viewModel.cancelUssdConsultation(lineId, sessionId)
                return false
            }
            return true
        }

        suspend fun trySilentRechargeQuery(plan: DataPlanEntity): Boolean {
            if (!viewModel.needsRechargeQuery(plan)) return false
            if (viewModel.uiState.value.consultingLineId != null) return false
            if (!UssdSystemApi.isAvailable(context)) return false
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.CALL_PHONE
                ) != PackageManager.PERMISSION_GRANTED
            ) return false

            val subscriptionId = if (plan.simSlotIndex != null) {
                if (!SimInfoProvider.hasPermission(context)) return false
                SimInfoProvider.subscriptionIdForSlot(context, plan.simSlotIndex) ?: return false
            } else {
                null
            }

            val sessionId = viewModel.startUssdConsultation(2, armAccessibility = false)
            val started = UssdSystemApi.request(
                context,
                PrepaidRecharge.USSD_CODE,
                subscriptionId
            ) { result ->
                when (result) {
                    is UssdSystemApi.Result.Success -> {
                        if (UssdParser.parseRechargeDate(result.response) != null) {
                            viewModel.applyUssdReading(2, result.response, sessionId)
                        } else {
                            viewModel.cancelUssdConsultation(2, sessionId)
                        }
                    }
                    is UssdSystemApi.Result.Failure,
                    UssdSystemApi.Result.Timeout -> viewModel.cancelUssdConsultation(2, sessionId)
                }
            }
            if (!started) {
                viewModel.cancelUssdConsultation(2, sessionId)
                return false
            }
            return true
        }

        // Lectura automática silenciosa al abrir: solo líneas sin lectura hoy.
        val current = viewModel.uiState.value
        val started1 = trySilentAutoRead(current, 1, current.line1.plan.ussdCode, current.line1.plan.simSlotIndex)
        if (started1) waitUntilIdle()
        val current2 = viewModel.uiState.value
        val started2 = trySilentAutoRead(current2, 2, current2.line2.plan.ussdCode, current2.line2.plan.simSlotIndex)
        if (started2) waitUntilIdle()

        // Prepaid only: one silent *222*732# query per purchase cycle.
        val current3 = viewModel.uiState.value
        trySilentRechargeQuery(current3.line2.plan)
    }

    // Si ya se registraron las lecturas de hoy, retiramos el recordatorio que
    // pudiera haber quedado visible de un día anterior o de una consulta hecha
    // justo después de que sonara el aviso de las 22:00.
    LaunchedEffect(state.logs) {
        if (hasReadingToday(state.logs, 1) && hasReadingToday(state.logs, 2)) {
            BalanceReadingReminderWorker.cancel(context)
        }
    }

    // "Consultar Saldo" en las pantallas principales: consulta y registra el
    // saldo sola, sin abrir el formulario. El formulario completo queda solo
    // accesible desde Ajustes.
    val line1Consult = rememberUssdConsultation(
        lineId = 1,
        ussdCode = state.line1.plan.ussdCode,
        selectedSimSlot = state.line1.plan.simSlotIndex,
        onStartAutoConsult = { useAccessibility ->
            viewModel.startUssdConsultation(1, useAccessibility)
        },
        onEnableAccessibilityFallback = { sessionId ->
            viewModel.enableAccessibilityFallback(1, sessionId)
        },
        onApplyAutoReading = { sessionId, text ->
            viewModel.applyUssdReading(1, text, sessionId)
        }
    )

    val line2Consult = rememberUssdConsultation(
        lineId = 2,
        ussdCode = state.line2.plan.ussdCode,
        selectedSimSlot = state.line2.plan.simSlotIndex,
        onStartAutoConsult = { useAccessibility ->
            viewModel.startUssdConsultation(2, useAccessibility)
        },
        onEnableAccessibilityFallback = { sessionId ->
            viewModel.enableAccessibilityFallback(2, sessionId)
        },
        onApplyAutoReading = { sessionId, text ->
            viewModel.applyUssdReading(2, text, sessionId)
        }
    )

    val rechargeConsult = rememberUssdConsultation(
        lineId = 2,
        ussdCode = PrepaidRecharge.USSD_CODE,
        selectedSimSlot = state.line2.plan.simSlotIndex,
        onStartAutoConsult = { useAccessibility ->
            viewModel.startUssdConsultation(2, useAccessibility)
        },
        onEnableAccessibilityFallback = { sessionId ->
            viewModel.enableAccessibilityFallback(2, sessionId)
        },
        onApplyAutoReading = { sessionId, text ->
            viewModel.applyUssdReading(2, text, sessionId)
        },
        successMessage = "Fecha de recarga actualizada."
    )

    if (selectedTab != 0) {
        BackHandler {
            selectedTab = 0
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("data_bottom_nav")
            ) {
                // Tab 0: Línea 1 (Mensual)
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        targetLineId = 1
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.SignalCellularAlt else Icons.Outlined.SignalCellularAlt,
                            contentDescription = "Línea 1"
                        )
                    },
                    label = { Text("Línea 1") },
                    modifier = Modifier.testTag("nav_tab_line1")
                )

                // Tab 1: Línea 2 (Prepago 35 días)
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        targetLineId = 2
                    },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.SimCard else Icons.Outlined.SimCard,
                            contentDescription = "Línea 2"
                        )
                    },
                    label = { Text("Línea 2") },
                    modifier = Modifier.testTag("nav_tab_line2")
                )

                // Tab 2: Lecturas (Historial de ambas líneas)
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "Lecturas"
                        )
                    },
                    label = { Text("Lecturas") },
                    modifier = Modifier.testTag("nav_tab_logs")
                )

                // Tab 3: Configurar
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 3) Icons.Filled.Tune else Icons.Outlined.Tune,
                            contentDescription = "Ajustes"
                        )
                    },
                    label = { Text("Ajustes") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> DataForecastScreen(
                    lineState = state.line1,
                    logs = state.logs,
                    onConsultBalance = { line1Consult.start() },
                    consultingBalance = state.consultingLineId == 1,
                    onOpenConfigurePackage = {
                        targetLineId = 1
                        showConfigurePackageDialog = true
                    },
                    onOpenAdjustDays = {
                        targetLineId = 1
                        showAdjustDaysDialog = true
                    },
                    onToggleDisplayUnit = { viewModel.toggleDisplayUnit(1) },
                    onSimulateDailyMB = { viewModel.setSimulatedDailyMB(1, it) }
                )
                1 -> Prepaid35LineScreen(
                    lineState = state.line2,
                    logs = state.logs,
                    onConsultBalance = { line2Consult.start() },
                    consultingBalance = state.consultingLineId == 2,
                    onConsultRechargeDate = { rechargeConsult.start() },
                    consultingRechargeDate = state.consultingLineId == 2,
                    onOpenConfigurePackage = {
                        targetLineId = 2
                        showConfigurePackageDialog = true
                    },
                    onOpenAdjustDays = {
                        targetLineId = 2
                        showAdjustDaysDialog = true
                    },
                    onOpenNewPurchaseDialog = {
                        targetLineId = 2
                        showNewPurchaseDialog = true
                    },
                    onToggleDisplayUnit = { viewModel.toggleDisplayUnit(2) },
                    onSimulateDailyMB = { viewModel.setSimulatedDailyMB(2, it) }
                )
                2 -> DataLogsScreen(
                    state = state,
                    onOpenAddLog = { lineId ->
                        targetLineId = lineId
                        showAddLogDialog = true
                    },
                    onDeleteLog = { viewModel.deleteLog(it) }
                )
                3 -> DataSettingsScreen(
                    state = state,
                    onOpenConfigurePackage = { lineId ->
                        targetLineId = lineId
                        showConfigurePackageDialog = true
                    },
                    onOpenUssdReading = { lineId ->
                        targetLineId = lineId
                        showUssdReadingDialog = true
                    },
                    onSelectSim = { lineId, simSlotIndex ->
                        viewModel.updateSimSlot(lineId, simSlotIndex)
                    },
                    onToggleUnit = { lineId -> viewModel.toggleDisplayUnit(lineId) },
                    onResetToCalendar = { lineId -> viewModel.resetToRealCalendar(lineId) },
                    updateState = updateState,
                    onCheckUpdates = { scope.launch { updateManager.check(force = true) } },
                    onDownloadUpdate = { updateManager.download() },
                    onInstallUpdate = { tryInstallUpdate() },
                    onDismissUpdate = { updateManager.dismiss() },
                    onOpenRelease = {
                        val url = updateState.info?.releaseUrl
                        if (!url.isNullOrBlank()) {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                // Sin navegador instalado: no hacemos nada.
                            }
                        }
                    }
                )
            }
        }

        // Active line state for dialogs
        val currentDialogLine = if (targetLineId == 1) state.line1 else state.line2

        // Dialogs
        if (showConfigurePackageDialog) {
            ConfigurePackageDialog(
                currentPackageTotalMB = currentDialogLine.plan.packageTotalMB,
                currentRemainingMB = currentDialogLine.plan.remainingBalanceMB,
                currentTotalDays = currentDialogLine.plan.totalCycleDays,
                displayUnit = currentDialogLine.displayUnit,
                monthlyCalendarCycle = targetLineId == 1,
                onDismiss = { showConfigurePackageDialog = false },
                onConfirm = { packageMB, remainingMB, totalDays ->
                    viewModel.updatePackageConfiguration(targetLineId, packageMB, remainingMB, totalDays)
                    showConfigurePackageDialog = false
                }
            )
        }

        if (showAdjustDaysDialog) {
            AdjustCycleDaysDialog(
                currentDay = currentDialogLine.forecast.daysElapsed,
                totalCycleDays = currentDialogLine.forecast.totalCycleDays,
                monthlyCalendarCycle = targetLineId == 1,
                onDismiss = { showAdjustDaysDialog = false },
                onConfirm = { cd, td ->
                    viewModel.updateDays(targetLineId, cd, td)
                    showAdjustDaysDialog = false
                },
                onResetCalendar = {
                    viewModel.resetToRealCalendar(targetLineId)
                }
            )
        }

        if (showNewPurchaseDialog) {
            NewPrepaidPurchaseDialog(
                currentPackageTotalMB = state.line2.plan.packageTotalMB,
                currentRemainingMB = state.line2.plan.remainingBalanceMB,
                displayUnit = state.line2.displayUnit,
                previousBalanceLost = state.line2.forecast.daysRemaining <= 0,
                onDismiss = { showNewPurchaseDialog = false },
                onConfirm = { packageMB, totalDays, purchaseDateMillis ->
                    viewModel.registerNewPrepaidPurchase(2, packageMB, totalDays, purchaseDateMillis)
                    RechargeReminderWorker.cancel(context)
                    showNewPurchaseDialog = false
                }
            )
        }

        if (showAddLogDialog) {
            AddBalanceLogDialog(
                currentDay = currentDialogLine.forecast.daysElapsed,
                displayUnit = currentDialogLine.displayUnit,
                initialLineId = targetLineId,
                onDismiss = { showAddLogDialog = false },
                onConfirm = { lineId, mb, day, note ->
                    viewModel.addManualLog(lineId, mb, day, note)
                    showAddLogDialog = false
                }
            )
        }

        if (showUssdReadingDialog) {
            UssdReadingDialog(
                lineId = targetLineId,
                lineName = if (targetLineId == 1) "Línea 1 (Mensual)" else "Línea 2 (Prepago 35d)",
                currentUssdCode = currentDialogLine.plan.ussdCode,
                packageTotalMB = currentDialogLine.plan.packageTotalMB,
                totalCycleDays = currentDialogLine.plan.totalCycleDays,
                displayUnit = currentDialogLine.displayUnit,
                selectedSimSlot = currentDialogLine.plan.simSlotIndex,
                autoReading = state.lastAutoReading?.takeIf { it.lineId == targetLineId },
                onDismiss = { showUssdReadingDialog = false },
                onSaveUssdCode = { code ->
                    viewModel.updateUssdCode(targetLineId, code)
                },
                onSelectSim = { slot ->
                    viewModel.updateSimSlot(targetLineId, slot)
                },
                onStartAutoConsult = { useAccessibility ->
                    viewModel.startUssdConsultation(targetLineId, useAccessibility)
                },
                onEnableAccessibilityFallback = { sessionId ->
                    viewModel.enableAccessibilityFallback(targetLineId, sessionId)
                },
                onApplyAutoReading = { sessionId, text ->
                    viewModel.applyUssdReading(targetLineId, text, sessionId)
                },
                onConfirmReading = { remainingMB, note ->
                    viewModel.recordUssdReading(targetLineId, remainingMB, note)
                    showUssdReadingDialog = false
                }
            )
        }
    }
}
