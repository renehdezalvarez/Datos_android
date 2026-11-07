package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.DataPlanRepository
import com.example.ui.components.AddBalanceLogDialog
import com.example.ui.components.AdjustCycleDaysDialog
import com.example.ui.components.ConfigurePackageDialog
import com.example.ui.components.NewPrepaidPurchaseDialog
import com.example.ui.components.UpdateBalanceDialog
import com.example.ui.screens.DataForecastScreen
import com.example.ui.screens.DataLogsScreen
import com.example.ui.screens.DataSettingsScreen
import com.example.ui.screens.Prepaid35LineScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.DataForecastViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = DataPlanRepository(database.dataDao())
        val viewModelFactory = DataForecastViewModel.provideFactory(repository)

        setContent {
            MyApplicationTheme {
                val viewModel: DataForecastViewModel = viewModel(factory = viewModelFactory)
                MainDataContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainDataContent(viewModel: DataForecastViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var targetLineId by rememberSaveable { mutableIntStateOf(1) }

    var showUpdateBalanceDialog by remember { mutableStateOf(false) }
    var showConfigurePackageDialog by remember { mutableStateOf(false) }
    var showAdjustDaysDialog by remember { mutableStateOf(false) }
    var showAddLogDialog by remember { mutableStateOf(false) }
    var showNewPurchaseDialog by remember { mutableStateOf(false) }

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
                            contentDescription = "Prepago 35d"
                        )
                    },
                    label = { Text("Prepago 35d") },
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
                    onOpenUpdateBalance = {
                        targetLineId = 1
                        showUpdateBalanceDialog = true
                    },
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
                    onOpenUpdateBalance = {
                        targetLineId = 2
                        showUpdateBalanceDialog = true
                    },
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
                    onToggleUnit = { lineId -> viewModel.toggleDisplayUnit(lineId) },
                    onResetToCalendar = { lineId -> viewModel.resetToRealCalendar(lineId) }
                )
            }
        }

        // Active line state for dialogs
        val currentDialogLine = if (targetLineId == 1) state.line1 else state.line2

        // Dialogs
        if (showUpdateBalanceDialog) {
            UpdateBalanceDialog(
                currentRemainingMB = currentDialogLine.plan.remainingBalanceMB,
                packageTotalMB = currentDialogLine.plan.packageTotalMB,
                displayUnit = currentDialogLine.displayUnit,
                onDismiss = { showUpdateBalanceDialog = false },
                onConfirm = { newMB ->
                    viewModel.updateRemainingBalance(targetLineId, newMB)
                    showUpdateBalanceDialog = false
                }
            )
        }

        if (showConfigurePackageDialog) {
            ConfigurePackageDialog(
                currentPackageTotalMB = currentDialogLine.plan.packageTotalMB,
                currentRemainingMB = currentDialogLine.plan.remainingBalanceMB,
                currentTotalDays = currentDialogLine.plan.totalCycleDays,
                displayUnit = currentDialogLine.displayUnit,
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
                onDismiss = { showNewPurchaseDialog = false },
                onConfirm = { packageMB, totalDays ->
                    viewModel.registerNewPrepaidPurchase(2, packageMB, totalDays)
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
    }
}
