package com.Pinak.ProductiveClub

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.Pinak.ProductiveClub.data.AppDatabase
import com.Pinak.ProductiveClub.ui.drawer.AppSidebar
import com.Pinak.ProductiveClub.ui.drawer.SidebarTab
import com.Pinak.ProductiveClub.ui.friends.FriendsScreen
import com.Pinak.ProductiveClub.ui.home.HomeScreen
import com.Pinak.ProductiveClub.ui.profile.OnboardingDialog
import com.Pinak.ProductiveClub.ui.profile.ProfileScreen
import com.Pinak.ProductiveClub.ui.settings.SettingsScreen
import com.Pinak.ProductiveClub.ui.summary.DailySummaryScreen
import com.Pinak.ProductiveClub.ui.summary.SummaryViewModel
import com.Pinak.ProductiveClub.ui.theme.MyApplicationTheme
import com.Pinak.ProductiveClub.ui.timetable.TaskViewModel
import com.Pinak.ProductiveClub.ui.timetable.TimetableScreen
import com.Pinak.ProductiveClub.util.AuthManager
import com.Pinak.ProductiveClub.util.FriendManager
import com.Pinak.ProductiveClub.util.NetworkHelper
import com.Pinak.ProductiveClub.util.ReminderManager
import kotlinx.coroutines.launch

class TaskViewModelFactory(
    private val taskDao: com.Pinak.ProductiveClub.data.TaskDao,
    private val reminderManager: ReminderManager
) :
    ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TaskViewModel(taskDao, reminderManager) as T
    }
}

enum class AuthState {
    Loading,
    Success,
    Failed
}

enum class OverlayScreen {
    None,
    DailySummary,
    Settings
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onPermissionUpdated()
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        onPermissionUpdated()
    }

    private var onPermissionUpdated: () -> Unit = {}

    fun hasNotificationPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

    fun hasCameraPermission(): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    fun hasUsageAccess(): Boolean =
        com.Pinak.ProductiveClub.util.ScreenTimeHelper.hasUsagePermission(this)

    fun hasExactAlarmPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.canScheduleExactAlarms() ?: true
        } else true

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        }
    }

    private var authState by mutableStateOf(AuthState.Loading)

    private fun startAuth() {
        authState = AuthState.Loading
        AuthManager.signInAnonymously { success ->
            if (success) {
                authState = AuthState.Success
                lifecycleScope.launch {
                    FriendManager.initialize(applicationContext)
                }
            } else {
                authState = AuthState.Failed
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        startAuth()

        val database = AppDatabase.getDatabase(applicationContext)
        val reminderManager = ReminderManager(applicationContext)
        val factory = TaskViewModelFactory(database.taskDao(), reminderManager)

        setContent {
            MyApplicationTheme {
                when (authState) {
                    AuthState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("🚀", style = MaterialTheme.typography.headlineLarge)
                                    }
                                }
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    "Connecting to Productive Club...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    AuthState.Failed -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
                                Text(
                                    "Connection Failed",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Could not connect to the cloud service. Please check your internet connection and try again.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { startAuth() },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Retry Connection")
                                }
                                TextButton(
                                    onClick = { NetworkHelper.openInternetPanel(applicationContext) }
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Network Settings")
                                }
                            }
                        }
                    }
                    AuthState.Success -> {
                        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                        val scope = rememberCoroutineScope()
                        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
                        var overlayScreen by rememberSaveable { mutableStateOf(OverlayScreen.None) }
                        var sidebarTab by remember { mutableStateOf(SidebarTab.Friends) }

                val summaryViewModel: SummaryViewModel = viewModel()
                val taskViewModel: TaskViewModel = viewModel(factory = factory)
                val tasks by taskViewModel.tasksForToday.collectAsState()

                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                var permRefreshKey by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            permRefreshKey++
                            taskViewModel.refreshDate()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    onPermissionUpdated = {
                        permRefreshKey++
                    }
                }

                val hasNotificationPerm = remember(permRefreshKey) { hasNotificationPermission() }
                val hasCameraPerm = remember(permRefreshKey) { hasCameraPermission() }
                val hasUsagePerm = remember(permRefreshKey) { hasUsageAccess() }
                val hasExactAlarmPerm = remember(permRefreshKey) { hasExactAlarmPermission() }

                // First-time Onboarding Dialog (Security, Permissions & Profile)
                var showOnboarding by remember { mutableStateOf(!FriendManager.isSecurityOnboarded(applicationContext)) }
                if (showOnboarding) {
                    val currentName by FriendManager.myDisplayName.collectAsState()
                    OnboardingDialog(
                        initialName = if (currentName.startsWith("Player ")) "" else currentName,
                        hasNotificationPermission = hasNotificationPerm,
                        hasCameraPermission = hasCameraPerm,
                        hasUsageAccess = hasUsagePerm,
                        hasExactAlarmPermission = hasExactAlarmPerm,
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        onRequestCamera = {
                            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onRequestUsageAccess = {
                            com.Pinak.ProductiveClub.util.ScreenTimeHelper.openUsageSettings(this@MainActivity)
                        },
                        onRequestExactAlarm = {
                            openExactAlarmSettings()
                        },
                        onComplete = { name, avatar ->
                            scope.launch {
                                FriendManager.updateMyProfile(applicationContext, name, avatar)
                                showOnboarding = false
                            }
                        }
                    )
                }

                // Native Hardware / Gesture Back Handling
                BackHandler(enabled = drawerState.isOpen) {
                    scope.launch { drawerState.close() }
                }

                BackHandler(enabled = overlayScreen != OverlayScreen.None && !drawerState.isOpen) {
                    if (overlayScreen == OverlayScreen.DailySummary) {
                        summaryViewModel.resetForm()
                        scope.launch { FriendManager.refreshFriendsAndGroups() }
                    }
                    overlayScreen = OverlayScreen.None
                }

                BackHandler(enabled = overlayScreen == OverlayScreen.None && pagerState.currentPage != 0 && !drawerState.isOpen) {
                    scope.launch { pagerState.animateScrollToPage(0) }
                }

                // Network connectivity observer
                val isOnline by NetworkHelper.observeNetwork(applicationContext)
                    .collectAsState(initial = NetworkHelper.isNetworkAvailable(applicationContext))
                var dismissedOfflineBanner by rememberSaveable { mutableStateOf(false) }

                LaunchedEffect(isOnline) {
                    if (isOnline) {
                        dismissedOfflineBanner = false
                    }
                }

                val snackbarHostState = remember { SnackbarHostState() }
                val initError by FriendManager.initError.collectAsState()

                LaunchedEffect(initError) {
                    initError?.let { err ->
                        val result = snackbarHostState.showSnackbar(
                            message = err,
                            actionLabel = "Retry",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            scope.launch {
                                FriendManager.refreshFriendsAndGroups()
                            }
                        }
                        FriendManager.clearError()
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppSidebar(
                            onCloseDrawer = { scope.launch { drawerState.close() } },
                            initialTab = sidebarTab
                        )
                    }
                ) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbarHostState) },
                        bottomBar = {
                            if (overlayScreen == OverlayScreen.None) {
                                NavigationBar {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                        label = { Text("Home") },
                                        selected = pagerState.currentPage == 0,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Schedule, contentDescription = "Timetable") },
                                        label = { Text("Timetable") },
                                        selected = pagerState.currentPage == 1,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Groups, contentDescription = "Friends") },
                                        label = { Text("Friends") },
                                        selected = pagerState.currentPage == 2,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(2) } }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                                        label = { Text("Profile") },
                                        selected = pagerState.currentPage == 3,
                                        onClick = { scope.launch { pagerState.animateScrollToPage(3) } }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (overlayScreen) {
                                OverlayScreen.DailySummary -> {
                                    DailySummaryScreen(
                                        viewModel = summaryViewModel,
                                        onBack = {
                                            summaryViewModel.resetForm()
                                            scope.launch {
                                                FriendManager.refreshFriendsAndGroups()
                                            }
                                            overlayScreen = OverlayScreen.None
                                        }
                                    )
                                }
                                OverlayScreen.Settings -> {
                                    SettingsScreen(
                                        onBack = { overlayScreen = OverlayScreen.None }
                                    )
                                }
                                OverlayScreen.None -> {
                                    // 4 Swipeable Primary Tabs
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize()
                                    ) { page ->
                                        when (page) {
                                            0 -> HomeScreen(
                                                summaryViewModel = summaryViewModel,
                                                taskViewModel = taskViewModel,
                                                onOpenDrawer = {
                                                    sidebarTab = SidebarTab.Friends
                                                    scope.launch { drawerState.open() }
                                                },
                                                onOpenDrawerWithTab = { tab ->
                                                    sidebarTab = tab
                                                    scope.launch { drawerState.open() }
                                                },
                                                onQuickAdd = {
                                                    sidebarTab = SidebarTab.Friends
                                                    scope.launch { drawerState.open() }
                                                },
                                                onNavigateToSummary = { overlayScreen = OverlayScreen.DailySummary },
                                                onNavigateToTimetable = { scope.launch { pagerState.animateScrollToPage(1) } },
                                                onNavigateToFriends = { scope.launch { pagerState.animateScrollToPage(2) } },
                                                onNavigateToSettings = { overlayScreen = OverlayScreen.Settings },
                                                isOnline = isOnline,
                                                onConnectInternet = {
                                                    NetworkHelper.openInternetPanel(applicationContext)
                                                }
                                            )
                                            1 -> TimetableScreen(
                                                viewModel = taskViewModel,
                                                onSummaryClick = { overlayScreen = OverlayScreen.DailySummary },
                                                onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                                                onOpenDrawer = {
                                                    sidebarTab = SidebarTab.Friends
                                                    scope.launch { drawerState.open() }
                                                }
                                            )
                                            2 -> FriendsScreen(
                                                isOnline = isOnline,
                                                onConnectInternet = {
                                                    NetworkHelper.openInternetPanel(applicationContext)
                                                }
                                            )
                                            3 -> ProfileScreen(
                                                summaryViewModel = summaryViewModel,
                                                tasks = tasks,
                                                onNavigateToSettings = { overlayScreen = OverlayScreen.Settings },
                                                onNavigateToSummary = { overlayScreen = OverlayScreen.DailySummary }
                                            )
                                        }
                                    }
                                }
                            }

                            // Non-Intrusive Offline Floating Banner
                            AnimatedVisibility(
                                visible = !isOnline && !dismissedOfflineBanner && overlayScreen == OverlayScreen.None,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                                    shadowElevation = 6.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Default.WifiOff,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB74D),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    "You are offline",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "Syncing paused until connected",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.75f)
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilledTonalButton(
                                                onClick = { NetworkHelper.openInternetPanel(applicationContext) },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text("Turn On", style = MaterialTheme.typography.labelSmall)
                                            }
                                            IconButton(
                                                onClick = { dismissedOfflineBanner = true },
                                                modifier = Modifier.size(32.dp).padding(start = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Dismiss",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
        }
    }


