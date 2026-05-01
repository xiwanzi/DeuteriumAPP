@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.deuterium.app

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deuterium.app.data.ChatFeedItem
import com.deuterium.app.data.ChatHistoryItem
import com.deuterium.app.data.OnlinePlayer
import com.deuterium.app.data.PlayerSummary
import com.deuterium.app.data.PlayerDirectoryItem
import com.deuterium.app.data.RepoResult
import com.deuterium.app.data.ResolvedPlayerRef
import com.deuterium.app.data.UserProfile
import com.deuterium.app.data.WalletRecord
import com.deuterium.app.network.ApiClient
import com.deuterium.app.repository.AppUpdateRepository
import com.deuterium.app.repository.AuthRepository
import com.deuterium.app.repository.ChatHistoryStore
import com.deuterium.app.repository.ChatRepository
import com.deuterium.app.repository.SessionStore
import com.deuterium.app.repository.WalletRepository
import com.deuterium.app.repository.formatIsoDateTimeUtc8
import com.deuterium.app.repository.playerSummary
import com.deuterium.app.repository.validateAmount
import com.deuterium.app.repository.validateChatMessage
import com.deuterium.app.ui.theme.DeuteriumColorPreset
import com.deuterium.app.ui.theme.DeuteriumTheme
import com.deuterium.app.ui.theme.DeuteriumThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestHighestRefreshRate()
        val preferences = AppPreferences(this)
        val chatHistoryStore = ChatHistoryStore(this)
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(preferences.loadThemeMode()) }
            var colorPreset by rememberSaveable { mutableStateOf(preferences.loadColorPreset()) }
            var dynamicColor by rememberSaveable { mutableStateOf(preferences.loadDynamicColor()) }
            DeuteriumTheme(
                themeMode = themeMode,
                colorPreset = colorPreset,
                dynamicColor = dynamicColor
            ) {
                DeuteriumApp(
                    preferences = preferences,
                    chatHistoryStore = chatHistoryStore,
                    appearance = AppAppearance(themeMode, colorPreset, dynamicColor),
                    onThemeModeChange = {
                        themeMode = it
                        preferences.saveThemeMode(it)
                    },
                    onColorPresetChange = {
                        colorPreset = it
                        preferences.saveColorPreset(it)
                    },
                    onDynamicColorChange = {
                        dynamicColor = it
                        preferences.saveDynamicColor(it)
                    }
                )
            }
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
    }

    private fun requestHighestRefreshRate() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        @Suppress("DEPRECATION")
        val fastestMode = windowManager.defaultDisplay.supportedModes.maxByOrNull { it.refreshRate } ?: return
        window.attributes = window.attributes.apply {
            preferredDisplayModeId = fastestMode.modeId
            preferredRefreshRate = fastestMode.refreshRate
        }
    }

    private companion object {
        const val NOTIFICATION_PERMISSION_REQUEST = 2001
    }
}

private data class AppAppearance(
    val themeMode: DeuteriumThemeMode,
    val colorPreset: DeuteriumColorPreset,
    val dynamicColor: Boolean
)

private class AppPreferences(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadThemeMode(): DeuteriumThemeMode {
        return prefs.getString(KEY_THEME_MODE, null)
            ?.let { runCatching { DeuteriumThemeMode.valueOf(it) }.getOrNull() }
            ?: DeuteriumThemeMode.System
    }

    fun saveThemeMode(value: DeuteriumThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, value.name).apply()
    }

    fun loadColorPreset(): DeuteriumColorPreset {
        return prefs.getString(KEY_COLOR_PRESET, null)
            ?.let { runCatching { DeuteriumColorPreset.valueOf(it) }.getOrNull() }
            ?: DeuteriumColorPreset.Emerald
    }

    fun saveColorPreset(value: DeuteriumColorPreset) {
        prefs.edit().putString(KEY_COLOR_PRESET, value.name).apply()
    }

    fun loadDynamicColor(): Boolean {
        return prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
    }

    fun saveDynamicColor(value: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()
    }

    fun loadShowServerEvents(): Boolean {
        return prefs.getBoolean(KEY_SHOW_SERVER_EVENTS, false)
    }

    fun saveShowServerEvents(value: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_SERVER_EVENTS, value).apply()
    }

    fun loadWalletNotifications(): Boolean {
        return prefs.getBoolean(KEY_WALLET_NOTIFICATIONS, false)
    }

    fun saveWalletNotifications(value: Boolean) {
        prefs.edit().putBoolean(KEY_WALLET_NOTIFICATIONS, value).apply()
    }

    override fun loadToken(): String? {
        return prefs.getString(KEY_SESSION_TOKEN, null)?.takeIf { it.isNotBlank() }
    }

    override fun loadUser(): UserProfile? {
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val playerRef = prefs.getString(KEY_USER_PLAYER_REF, null)?.takeIf { it.isNotBlank() } ?: return null
        val gameId = prefs.getString(KEY_USER_GAME_ID, null)?.takeIf { it.isNotBlank() } ?: return null
        val qq = prefs.getString(KEY_USER_QQ, null) ?: return null
        val identityStatus = prefs.getString(KEY_USER_IDENTITY_STATUS, null) ?: "bound"
        return UserProfile(userId, playerRef, gameId, qq, identityStatus)
    }

    override fun saveSession(token: String, user: UserProfile) {
        prefs.edit()
            .putString(KEY_SESSION_TOKEN, token)
            .apply()
        saveUser(user)
    }

    override fun saveUser(user: UserProfile) {
        prefs.edit()
            .putString(KEY_USER_ID, user.userId)
            .putString(KEY_USER_PLAYER_REF, user.playerRef)
            .putString(KEY_USER_GAME_ID, user.gameId)
            .putString(KEY_USER_QQ, user.qq)
            .putString(KEY_USER_IDENTITY_STATUS, user.identityStatus)
            .apply()
    }

    override fun clearSession() {
        prefs.edit()
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_PLAYER_REF)
            .remove(KEY_USER_GAME_ID)
            .remove(KEY_USER_QQ)
            .remove(KEY_USER_IDENTITY_STATUS)
            .apply()
    }

    override fun loadLastWalletRecordId(userId: String): String? {
        return prefs.getString(walletRecordKey(userId), null)?.takeIf { it.isNotBlank() }
    }

    override fun saveLastWalletRecordId(userId: String, recordId: String) {
        prefs.edit().putString(walletRecordKey(userId), recordId).apply()
    }

    private fun walletRecordKey(userId: String): String = "$KEY_LAST_WALLET_RECORD_ID_PREFIX$userId"

    private companion object {
        const val PREFS_NAME = "deuterium_app_preferences"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_COLOR_PRESET = "color_preset"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_SHOW_SERVER_EVENTS = "show_server_events"
        const val KEY_WALLET_NOTIFICATIONS = "wallet_notifications"
        const val KEY_SESSION_TOKEN = "session_token"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_PLAYER_REF = "user_player_ref"
        const val KEY_USER_GAME_ID = "user_game_id"
        const val KEY_USER_QQ = "user_qq"
        const val KEY_USER_IDENTITY_STATUS = "user_identity_status"
        const val KEY_LAST_WALLET_RECORD_ID_PREFIX = "last_wallet_record_id_"
    }
}

private data class PendingTransfer(
    val recipient: ResolvedPlayerRef?,
    val amount: String,
    val note: String
)

private data class MentionSelection(
    val playerRef: String,
    val gameId: String
)

private enum class AuthMode { Login, Register, ResetPassword }
private enum class MainSection { Wallet, Transfer, Chat, Profile }
private enum class TransferFeedbackPhase { Idle, Loading, Success, Notice, Error }

private object AppShapes {
    val small = RoundedCornerShape(12.dp)
    val medium = RoundedCornerShape(16.dp)
    val large = RoundedCornerShape(24.dp)
    val extraLarge = RoundedCornerShape(28.dp)
    val list = RoundedCornerShape(20.dp)
    val pill = RoundedCornerShape(999.dp)
}

private object AppMotion {
    const val PressedScale = 0.96f
    const val Micro = 110
    const val Fast = 160
    const val Page = 240
    const val Dialog = 280
    val Easing = FastOutSlowInEasing
}

private fun MainSection.motionOrder(): Int = when (this) {
    MainSection.Wallet -> 0
    MainSection.Chat -> 1
    MainSection.Profile -> 2
    MainSection.Transfer -> 3
}


@Composable
private fun DeuteriumApp(
    preferences: AppPreferences,
    chatHistoryStore: ChatHistoryStore,
    appearance: AppAppearance,
    onThemeModeChange: (DeuteriumThemeMode) -> Unit,
    onColorPresetChange: (DeuteriumColorPreset) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = context as? LifecycleOwner
    val notifier = remember(context) { AppNotifier(context.applicationContext) }
    val apiClient = remember { ApiClient { preferences.loadToken() } }
    val authRepository = remember { AuthRepository(apiClient, preferences) }
    val walletRepository = remember { WalletRepository(apiClient, preferences) }
    val appUpdateRepository = remember { AppUpdateRepository(apiClient) }
    val appScope = rememberCoroutineScope()
    var user by remember { mutableStateOf(preferences.loadUser()) }
    var showServerEvents by rememberSaveable { mutableStateOf(preferences.loadShowServerEvents()) }
    var walletNotifications by rememberSaveable { mutableStateOf(preferences.loadWalletNotifications()) }
    val chatRepository = remember {
        ChatRepository(
            apiClient = apiClient,
            sessionStore = preferences,
            historyStore = chatHistoryStore,
            onUnauthorized = { user = null },
            walletNotificationsEnabled = { preferences.loadWalletNotifications() },
            onNotificationPermissionNeeded = { (context as? MainActivity)?.requestNotificationPermissionIfNeeded() },
            onFollowedChatNotification = { notifier.notifyFollowedChat(it) },
            onWalletNotification = { notifier.notifyWalletRecord(it) },
            onMentionNotification = { notifier.notifyMention(it) },
            onWalletRecord = { walletRepository.mergeRecord(it) },
            onSocketConnected = {
                appScope.launch { walletRepository.syncNewRecords() }
            }
        )
    }

    LaunchedEffect(Unit) {
        if (!preferences.loadToken().isNullOrBlank()) {
            when (val result = authRepository.restoreSession()) {
                is RepoResult.Success -> user = result.value
                is RepoResult.Error -> if (result.code == "UNAUTHORIZED") user = null
            }
        }
    }

    LaunchedEffect(user?.userId, showServerEvents) {
        if (user != null) {
            chatRepository.connect(showServerEvents)
            chatRepository.loadInitial()
            walletRepository.syncNewRecords()
        } else {
            chatRepository.disconnect()
        }
    }

    DisposableEffect(lifecycleOwner, user?.userId, showServerEvents) {
        if (lifecycleOwner == null || user == null) {
            onDispose {}
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        chatRepository.connect(showServerEvents)
                        chatRepository.reportAppForeground(true)
                        appScope.launch {
                            chatRepository.refreshPresenceAndDirectory()
                            walletRepository.syncNewRecords()
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> chatRepository.reportAppForeground(false)
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            chatRepository.reportAppForeground(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    AppBackground {
        AnimatedContent(
            targetState = user,
            transitionSpec = {
                (fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                    slideInHorizontally(tween(AppMotion.Page, easing = AppMotion.Easing)) { it / 8 })
                    .togetherWith(
                        fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                            slideOutHorizontally(tween(AppMotion.Fast, easing = AppMotion.Easing)) { -it / 10 }
                    )
            },
            label = "SessionTransition"
        ) { activeUser ->
            if (activeUser == null) {
                AuthScreen(
                    repository = authRepository,
                    onAuthenticated = { user = it }
                )
            } else {
                MainShell(
                    user = activeUser,
                    authRepository = authRepository,
                    walletRepository = walletRepository,
                    chatRepository = chatRepository,
                    appUpdateRepository = appUpdateRepository,
                    appearance = appearance,
                    onThemeModeChange = onThemeModeChange,
                    onColorPresetChange = onColorPresetChange,
                    onDynamicColorChange = onDynamicColorChange,
                    showServerEvents = showServerEvents,
                    onShowServerEventsChange = {
                        showServerEvents = it
                        preferences.saveShowServerEvents(it)
                    },
                    walletNotifications = walletNotifications,
                    onWalletNotificationsChange = {
                        walletNotifications = it
                        preferences.saveWalletNotifications(it)
                        if (it) (context as? MainActivity)?.requestNotificationPermissionIfNeeded()
                    },
                    onLogout = {
                        chatRepository.disconnect()
                        user = null
                    }
                )
            }
        }
    }
}

@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        content()
    }
}

@Composable
private fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.large,
    color: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = color,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
private fun AppIconButtonSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) AppMotion.PressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "IconButtonPressScale"
    )
    AppSurface(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = AppShapes.pill,
        color = if (selected) colors.primaryContainer else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.fillMaxSize(),
            interactionSource = interactionSource
        ) {
            content()
        }
    }
}

@Composable
private fun AuthScreen(
    repository: AuthRepository,
    onAuthenticated: (UserProfile) -> Unit
) {
    var mode by remember { mutableStateOf(AuthMode.Login) }
    val title = when (mode) {
        AuthMode.Login -> "登录 Deuterium"
        AuthMode.Register -> "注册并绑定服务器身份"
        AuthMode.ResetPassword -> "通过游戏内验证码重设密码"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Deuterium VIII",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "账号、信用点与聊天的手机入口",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        (fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                            slideInHorizontally(tween(AppMotion.Page, easing = AppMotion.Easing)) { it / 8 })
                            .togetherWith(
                                fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                                    slideOutHorizontally(tween(AppMotion.Fast, easing = AppMotion.Easing)) { -it / 10 }
                            )
                    },
                    label = "AuthModeTransition"
                ) { targetMode ->
                    when (targetMode) {
                        AuthMode.Login -> LoginForm(
                            repository = repository,
                            onAuthenticated = onAuthenticated,
                            onForgotPassword = { mode = AuthMode.ResetPassword }
                        )
                        AuthMode.Register -> RegisterForm(repository, onAuthenticated)
                        AuthMode.ResetPassword -> ResetPasswordForm(repository) {
                            mode = AuthMode.Login
                        }
                    }
                }
                Divider()
                AuthModeButtons(
                    mode = mode,
                    onModeChange = { mode = it }
                )
            }
        }
    }
}

@Composable
private fun LoginForm(
    repository: AuthRepository,
    onAuthenticated: (UserProfile) -> Unit,
    onForgotPassword: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = account,
            onValueChange = { account = it },
            label = { Text("玩家 ID 或 QQ 号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(
            onClick = onForgotPassword,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("忘记密码？", style = MaterialTheme.typography.labelLarge)
        }
        PrimaryActionButton(
            text = "登录",
            icon = Icons.Filled.Login,
            onClick = {
                if (loading) return@PrimaryActionButton
                scope.launch {
                    loading = true
                    message = null
                    when (val result = repository.login(account, password)) {
                        is RepoResult.Success -> onAuthenticated(result.value)
                        is RepoResult.Error -> message = result.message
                    }
                    loading = false
                }
            }
        )
        InlineMessage(message)
    }
}

@Composable
private fun RegisterForm(repository: AuthRepository, onAuthenticated: (UserProfile) -> Unit) {
    val scope = rememberCoroutineScope()
    var gameId by remember { mutableStateOf("") }
    var qq by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var verificationToken by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(gameId, { gameId = it }, label = { Text("游戏内 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qq, { qq = it }, label = { Text("QQ 号") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                if (loading) return@OutlinedButton
                scope.launch {
                    loading = true
                    message = null
                    when (val result = repository.requestRegisterCode(gameId, qq, password)) {
                        is RepoResult.Success -> {
                            verificationToken = result.value.verificationToken
                            message = "验证码已发送到服务器私聊，请在有效期内输入。"
                        }
                        is RepoResult.Error -> message = result.message
                    }
                    loading = false
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("发送")
            }
        }
        PrimaryActionButton(
            text = "完成注册",
            icon = Icons.Filled.PersonAdd,
            onClick = {
                if (loading) return@PrimaryActionButton
                scope.launch {
                    loading = true
                    message = null
                    when (val result = repository.register(verificationToken, code, password)) {
                        is RepoResult.Success -> onAuthenticated(result.value)
                        is RepoResult.Error -> message = result.message
                    }
                    loading = false
                }
            }
        )
        InlineMessage(message)
    }
}

@Composable
private fun ResetPasswordForm(
    repository: AuthRepository,
    initialAccount: String = "",
    accountReadOnly: Boolean = false,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var account by remember(initialAccount) { mutableStateOf(initialAccount) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationToken by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = account,
            onValueChange = { if (!accountReadOnly) account = it },
            label = { Text("玩家 ID 或 QQ 号") },
            singleLine = true,
            enabled = !accountReadOnly,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { if (it.length <= 6) code = it },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(onClick = {
                if (loading) return@OutlinedButton
                scope.launch {
                    loading = true
                    message = null
                    when (val result = repository.requestResetCode(account)) {
                        is RepoResult.Success -> {
                            verificationToken = result.value.verificationToken
                            message = "改密验证码已发送到服务器私聊，请在有效期内输入。"
                        }
                        is RepoResult.Error -> message = result.message
                    }
                    loading = false
                }
            }) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("发送")
            }
        }
        OutlinedTextField(password, { password = it }, label = { Text("新密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        PrimaryActionButton(
            text = "重设密码",
            icon = Icons.Filled.Lock,
            onClick = {
                if (loading) return@PrimaryActionButton
                scope.launch {
                    loading = true
                    message = null
                    when (val result = repository.resetPassword(verificationToken, code, password)) {
                        is RepoResult.Success -> {
                            message = result.value
                            onDone()
                        }
                        is RepoResult.Error -> message = result.message
                    }
                    loading = false
                }
            }
        )
        InlineMessage(message)
    }
}

@Composable
private fun AuthModeButtons(mode: AuthMode, onModeChange: (AuthMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        TextButton(
            onClick = { onModeChange(AuthMode.Login) },
            enabled = mode != AuthMode.Login
        ) {
            Icon(Icons.Filled.Login, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("登录")
        }
        TextButton(
            onClick = { onModeChange(AuthMode.Register) },
            enabled = mode != AuthMode.Register
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("注册")
        }
    }
}

@Composable
private fun MainShell(
    user: UserProfile,
    authRepository: AuthRepository,
    walletRepository: WalletRepository,
    chatRepository: ChatRepository,
    appUpdateRepository: AppUpdateRepository,
    appearance: AppAppearance,
    onThemeModeChange: (DeuteriumThemeMode) -> Unit,
    onColorPresetChange: (DeuteriumColorPreset) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    showServerEvents: Boolean,
    onShowServerEventsChange: (Boolean) -> Unit,
    walletNotifications: Boolean,
    onWalletNotificationsChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(MainSection.Wallet) }
    var transferRecipient by remember { mutableStateOf<ResolvedPlayerRef?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val chatListState = rememberLazyListState()
    var chatFollowLatest by rememberSaveable { mutableStateOf(true) }
    var chatUnseenMessages by rememberSaveable { mutableStateOf(0) }
    var chatObservedVisibleCount by rememberSaveable { mutableStateOf(0) }
    var chatObservedLastVisibleId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = AppShapes.extraLarge
            ) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Text("Deuterium", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = user.gameId,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = !imeVisible,
                enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                    expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing), expandFrom = Alignment.Top),
                exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                    shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing), shrinkTowards = Alignment.Top)
            ) {
                AppSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = AppShapes.extraLarge
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomItem("钱包", Icons.Filled.Home, section == MainSection.Wallet) { section = MainSection.Wallet }
                        BottomItem("聊天", Icons.Filled.Chat, section == MainSection.Chat) { section = MainSection.Chat }
                        BottomItem("我的", Icons.Filled.Person, section == MainSection.Profile) { section = MainSection.Profile }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = section,
                transitionSpec = {
                    val forward = targetState.motionOrder() >= initialState.motionOrder()
                    val enterOffset: (Int) -> Int = { if (forward) it / 7 else -it / 7 }
                    val exitOffset: (Int) -> Int = { if (forward) -it / 9 else it / 9 }
                    (fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                        slideInHorizontally(tween(AppMotion.Page, easing = AppMotion.Easing), enterOffset))
                        .togetherWith(
                            fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                                slideOutHorizontally(tween(AppMotion.Fast, easing = AppMotion.Easing), exitOffset)
                        )
                },
                modifier = Modifier.fillMaxSize(),
                label = "MainSectionTransition"
            ) { targetSection ->
                when (targetSection) {
                    MainSection.Wallet -> WalletScreen(
                        repository = walletRepository,
                        onTransfer = {
                            transferRecipient = null
                            section = MainSection.Transfer
                        }
                    )
                    MainSection.Transfer -> TransferScreen(
                        repository = walletRepository,
                        initialRecipient = transferRecipient,
                        onBack = { section = MainSection.Wallet }
                    )
                    MainSection.Chat -> ChatScreen(
                        repository = chatRepository,
                        showServerEvents = showServerEvents,
                        listState = chatListState,
                        followLatest = chatFollowLatest,
                        onFollowLatestChange = { chatFollowLatest = it },
                        unseenMessages = chatUnseenMessages,
                        onUnseenMessagesChange = { chatUnseenMessages = it },
                        observedVisibleCount = chatObservedVisibleCount,
                        onObservedVisibleCountChange = { chatObservedVisibleCount = it },
                        observedLastVisibleId = chatObservedLastVisibleId,
                        onObservedLastVisibleIdChange = { chatObservedLastVisibleId = it },
                        onTransferToPlayer = {
                            transferRecipient = it
                            section = MainSection.Transfer
                        }
                    )
                    MainSection.Profile -> ProfileScreen(
                        user = user,
                        repository = authRepository,
                        chatRepository = chatRepository,
                        appUpdateRepository = appUpdateRepository,
                        appearance = appearance,
                        onThemeModeChange = onThemeModeChange,
                        onColorPresetChange = onColorPresetChange,
                        onDynamicColorChange = onDynamicColorChange,
                        showServerEvents = showServerEvents,
                        onShowServerEventsChange = onShowServerEventsChange,
                        walletNotifications = walletNotifications,
                        onWalletNotificationsChange = onWalletNotificationsChange,
                        onLogout = {
                            scope.launch {
                                authRepository.logout()
                                onLogout()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletScreen(repository: WalletRepository, onTransfer: () -> Unit) {
    var message by remember { mutableStateOf<String?>(null) }
    var autoDismissMessage by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        when (val result = repository.loadWallet()) {
            is RepoResult.Success -> {
                message = null
                autoDismissMessage = false
            }
            is RepoResult.Error -> {
                message = result.message
                autoDismissMessage = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AppSurface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = AppShapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("信用点余额", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = repository.balance?.amount ?: "正在加载",
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            scope.launch {
                                message = when (val result = repository.refreshBalance()) {
                                    is RepoResult.Success -> {
                                        autoDismissMessage = true
                                        "余额已刷新：${result.value.amount}"
                                    }
                                    is RepoResult.Error -> {
                                        autoDismissMessage = false
                                        result.message
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("刷新")
                        }
                    }
                }
            }
        }
        item {
            PrimaryActionButton("发起转账", Icons.Filled.Add, onTransfer)
            InlineMessage(
                message = message,
                autoDismiss = autoDismissMessage,
                onDismiss = {
                    message = null
                    autoDismissMessage = false
                }
            )
        }
        item {
            Text("最近流水", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(
            items = repository.records,
            key = { it.recordId }
        ) { record ->
            WalletRecordRow(record, Modifier.animateItem())
        }
    }
}

@Composable
private fun TransferScreen(
    repository: WalletRepository,
    initialRecipient: ResolvedPlayerRef?,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember(initialRecipient) { mutableStateOf(initialRecipient?.gameId ?: "") }
    var recipient by remember(initialRecipient) { mutableStateOf(initialRecipient) }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showConfirm by remember { mutableStateOf(false) }
    var pendingTransfer by remember { mutableStateOf<PendingTransfer?>(null) }
    var feedbackPhase by remember { mutableStateOf(TransferFeedbackPhase.Idle) }
    var feedbackMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("转账", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onBack) { Text("返回钱包") }
        }
        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("收款玩家", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("游戏内 ID 或 QQ") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(onClick = {
                        scope.launch {
                            when (val result = repository.findRecipient(query)) {
                                is RepoResult.Success -> {
                                    recipient = result.value
                                    message = "已确认收款方：${result.value.gameId}"
                                }
                                is RepoResult.Error -> {
                                    recipient = null
                                    message = result.message
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }
                AnimatedVisibility(
                    visible = recipient != null,
                    enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                        expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
                    exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                        shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing))
                ) {
                    recipient?.let {
                        PlayerSummaryCard(player = playerSummary(it))
                    }
                }
            }
        }
        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("转账信息", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注，可选") },
                    modifier = Modifier.fillMaxWidth()
                )
                PrimaryActionButton("二次确认", Icons.Filled.Check) {
                    val amountError = validateAmount(amount)
                    message = when {
                        recipient == null -> "请先确认收款玩家。"
                        amountError != null -> amountError
                        else -> {
                            showConfirm = true
                            null
                        }
                    }
                }
            }
        }
        InlineMessage(message)
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("确认转账") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("收款方：${recipient?.gameId.orEmpty()}")
                    Text("金额：${amount.ifBlank { "0" }} 信用点")
                    Text("备注：${note.ifBlank { "无" }}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showConfirm = false
                    message = null
                    pendingTransfer = PendingTransfer(recipient, amount, note)
                    feedbackMessage = "正在确认转账"
                    feedbackPhase = TransferFeedbackPhase.Loading
                    scope.launch {
                        val pending = PendingTransfer(recipient, amount, note)
                        when (val result = repository.transfer(pending.recipient, pending.amount, pending.note)) {
                            is RepoResult.Success -> {
                                val transfer = result.value
                                feedbackMessage = when (transfer.status) {
                                    "success" -> "已转出 ${transfer.amount} 信用点"
                                    "processing" -> "转账正在处理中，请稍后查看余额和流水。"
                                    "unknown" -> "转账结果暂未确认，请稍后查看余额和流水。"
                                    else -> "转账未完成，请查看余额和流水确认结果。"
                                }
                                feedbackPhase = if (transfer.status == "success") {
                                    TransferFeedbackPhase.Success
                                } else {
                                    TransferFeedbackPhase.Notice
                                }
                            }
                            is RepoResult.Error -> {
                                feedbackMessage = result.message
                                feedbackPhase = TransferFeedbackPhase.Error
                            }
                        }
                    }
                }) {
                    Text("确认提交")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text("继续修改")
                }
            }
        )
    }

    if (feedbackPhase != TransferFeedbackPhase.Idle) {
        TransferFeedbackDialog(
            phase = feedbackPhase,
            message = feedbackMessage,
            onDismiss = {
                feedbackPhase = TransferFeedbackPhase.Idle
                pendingTransfer = null
            }
        )
    }
}

@Composable
private fun TransferFeedbackDialog(
    phase: TransferFeedbackPhase,
    message: String,
    onDismiss: () -> Unit
) {
    val loading = phase == TransferFeedbackPhase.Loading
    val success = phase == TransferFeedbackPhase.Success
    val loadingTransition = rememberInfiniteTransition(label = "TransferLoadingTransition")
    val loadingRotation by loadingTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(720, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "TransferLoadingRotation"
    )
    val iconScale by animateFloatAsState(
        targetValue = when (phase) {
            TransferFeedbackPhase.Success -> 1.12f
            TransferFeedbackPhase.Error -> 1.02f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "TransferFeedbackIconScale"
    )
    val errorShake = remember { Animatable(0f) }

    LaunchedEffect(phase) {
        if (phase == TransferFeedbackPhase.Error) {
            errorShake.snapTo(0f)
            repeat(3) {
                errorShake.animateTo(7f, tween(42, easing = LinearEasing))
                errorShake.animateTo(-7f, tween(42, easing = LinearEasing))
            }
            errorShake.animateTo(0f, tween(42, easing = LinearEasing))
        } else {
            errorShake.snapTo(0f)
        }
    }

    val iconMotion = Modifier.graphicsLayer {
        rotationZ = if (loading) loadingRotation else 0f
        scaleX = iconScale
        scaleY = iconScale
        translationX = errorShake.value
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        icon = {
            when (phase) {
                TransferFeedbackPhase.Loading -> Box(
                    modifier = iconMotion
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
                TransferFeedbackPhase.Success -> Box(
                    modifier = iconMotion
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }
                TransferFeedbackPhase.Notice -> Box(
                    modifier = iconMotion
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
                TransferFeedbackPhase.Error -> Box(
                    modifier = iconMotion
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(34.dp)
                    )
                }
                TransferFeedbackPhase.Idle -> Spacer(Modifier.size(54.dp))
            }
        },
        title = {
            Text(
                when (phase) {
                    TransferFeedbackPhase.Loading -> "正在转账"
                    TransferFeedbackPhase.Success -> "转账成功"
                    TransferFeedbackPhase.Notice -> "结果待确认"
                    TransferFeedbackPhase.Error -> "转账失败"
                    TransferFeedbackPhase.Idle -> ""
                }
            )
        },
        text = {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            if (!loading) {
                Button(onClick = onDismiss) {
                    Text(if (success) "完成" else "返回修改")
                }
            }
        }
    )
}

@Composable
private fun ChatScreen(
    repository: ChatRepository,
    showServerEvents: Boolean,
    listState: LazyListState,
    followLatest: Boolean,
    onFollowLatestChange: (Boolean) -> Unit,
    unseenMessages: Int,
    onUnseenMessagesChange: (Int) -> Unit,
    observedVisibleCount: Int,
    onObservedVisibleCountChange: (Int) -> Unit,
    observedLastVisibleId: String?,
    onObservedLastVisibleIdChange: (String?) -> Unit,
    onTransferToPlayer: (ResolvedPlayerRef) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(TextFieldValue("")) }
    var message by remember { mutableStateOf<String?>(null) }
    var autoDismissMessage by remember { mutableStateOf(false) }
    var selectedPlayer by remember { mutableStateOf<PlayerDirectoryItem?>(null) }
    var selectedMentions by remember { mutableStateOf<List<MentionSelection>>(emptyList()) }
    var showMentions by remember { mutableStateOf(false) }
    var showPlayerDirectory by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val keyboardPadding = with(density) {
        (
            WindowInsets.ime.getBottom(this) -
                WindowInsets.navigationBars.getBottom(this)
            )
            .coerceAtLeast(0)
            .toDp()
    }
    val mentionQuery = activeMentionQuery(input)
    val mentionPlayers = repository.mentionCandidates(mentionQuery)
    val visibleMessages = repository.messages.filter { showServerEvents || !it.event }

    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            val atBottom = layout.totalItemsCount == 0 || lastVisibleIndex >= layout.totalItemsCount - 1
            listState.isScrollInProgress to atBottom
        }.collect { (scrolling, atBottom) ->
            if (scrolling) {
                onFollowLatestChange(atBottom)
            } else if (atBottom) {
                onFollowLatestChange(true)
                onUnseenMessagesChange(0)
            }
        }
    }

    LaunchedEffect(visibleMessages.size, visibleMessages.lastOrNull()?.id) {
        val currentLastId = visibleMessages.lastOrNull()?.id
        val insertedCount = (visibleMessages.size - observedVisibleCount).coerceAtLeast(0)
        if (observedLastVisibleId != null && currentLastId != observedLastVisibleId && insertedCount > 0 && !followLatest) {
            onUnseenMessagesChange(unseenMessages + insertedCount)
        }
        if (followLatest && visibleMessages.isNotEmpty()) {
            listState.scrollToItem(visibleMessages.lastIndex)
            onUnseenMessagesChange(0)
        }
        onObservedVisibleCountChange(visibleMessages.size)
        onObservedLastVisibleIdChange(currentLastId)
    }

    LaunchedEffect(Unit) {
        if (repository.messages.isEmpty()) {
            repository.syncRecentMessages()
        }
        repository.refreshPresenceAndDirectory()
        repository.refreshFollows()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = keyboardPadding)
        ) {
            AppSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = AppShapes.pill
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppIconButtonSurface(
                        modifier = Modifier
                            .height(40.dp)
                            .widthIn(min = 76.dp),
                        onClick = { showPlayerDirectory = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(Icons.Filled.People, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text(
                                (repository.onlineCount ?: repository.playerDirectory.count { it.serverOnline }).toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        "公共聊天",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = visibleMessages,
                    key = { it.id }
                ) { chat ->
                    ChatBubble(
                        message = chat,
                        onLongCopy = {
                            clipboardManager.setText(AnnotatedString(chat.content))
                            message = if (chat.event) "已复制服务器事件。" else "已复制 ${chat.sender} 的消息。"
                            autoDismissMessage = true
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = (showMentions || mentionQuery != null) && mentionPlayers.isNotEmpty(),
                enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                    expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
                exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                    shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing))
            ) {
                MentionSuggestions(
                    players = mentionPlayers,
                    onSelect = { player ->
                        input = insertMention(input, player.gameId)
                        selectedMentions = (selectedMentions + MentionSelection(player.playerRef, player.gameId))
                            .distinctBy { it.playerRef }
                        showMentions = false
                    }
                )
            }

            AnimatedVisibility(
                visible = unseenMessages > 0,
                enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                    expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
                exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                    shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AssistChip(
                        onClick = {
                            scope.launch {
                                if (visibleMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(visibleMessages.lastIndex)
                                }
                                onFollowLatestChange(true)
                                onUnseenMessagesChange(0)
                            }
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        label = { Text("$unseenMessages 条新消息") }
                    )
                }
            }

            InlineMessage(
                message = message ?: repository.connectionMessage,
                modifier = Modifier.padding(horizontal = 16.dp),
                autoDismiss = message != null && autoDismissMessage,
                onDismiss = {
                    message = null
                    autoDismissMessage = false
                }
            )
            ChatInputBar(
                input = input,
                onInputChange = {
                    input = it
                    selectedMentions = selectedMentions.filter { mention ->
                        it.text.contains("@${mention.gameId}")
                    }
                    showMentions = activeMentionQuery(it) != null
                },
                onMentionClick = {
                    if (showMentions) {
                        showMentions = false
                    } else {
                        if (activeMentionQuery(input) == null) {
                            input = appendMentionMarker(input)
                        }
                        showMentions = true
                    }
                },
                onSend = {
                    when (val result = repository.send(input.text, selectedMentions.map { it.playerRef })) {
                        is RepoResult.Success -> {
                            input = TextFieldValue("")
                            selectedMentions = emptyList()
                            showMentions = false
                            message = null
                            autoDismissMessage = false
                        }
                        is RepoResult.Error -> {
                            message = result.message
                            autoDismissMessage = false
                        }
                    }
                }
            )
        }

        if (showPlayerDirectory) {
            PlayerDirectoryPanel(
                players = repository.playerDirectory,
                onDismiss = { showPlayerDirectory = false },
                onInspect = { player ->
                    selectedPlayer = player
                    showPlayerDirectory = false
                }
            )
        }
    }

    selectedPlayer?.let { player ->
        AlertDialog(
            onDismissRequest = { selectedPlayer = null },
            title = { Text(player.gameId) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("QQ：${player.qq ?: "未绑定或不可见"}")
                    Text(playerDirectoryPrimaryLabel(player))
                    Text(playerDirectoryStatusLabel(player))
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        enabled = !player.self,
                        onClick = {
                            scope.launch {
                                when (val result = repository.toggleFollow(player)) {
                                    is RepoResult.Success -> {
                                        val followed = result.value
                                        selectedPlayer = player.copy(followed = followed)
                                        message = if (followed) "已关心 ${player.gameId}。" else "已取消关心 ${player.gameId}。"
                                        autoDismissMessage = true
                                    }
                                    is RepoResult.Error -> {
                                        message = result.message
                                        autoDismissMessage = false
                                    }
                                }
                            }
                        }
                    ) {
                        Text(if (player.followed) "取消关心" else "关心")
                    }
                    Button(
                        enabled = !player.self,
                        onClick = {
                            selectedPlayer = null
                            onTransferToPlayer(
                                ResolvedPlayerRef(
                                    playerRef = player.playerRef,
                                    gameId = player.gameId,
                                    qq = player.qq,
                                    online = player.serverOnline,
                                    registered = player.registered,
                                    source = "player_directory"
                                )
                            )
                        }
                    ) {
                        Text("向他转账")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPlayer = null }) {
                    Text("关闭")
                }
            }
        )
    }
}

@Composable
private fun PlayerDirectoryPanel(
    players: List<PlayerDirectoryItem>,
    onDismiss: () -> Unit,
    onInspect: (PlayerDirectoryItem) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("玩家列表", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${players.count { it.serverOnline }} 位服务器在线，${players.size} 位可查看玩家",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭玩家列表")
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(
                        items = players,
                        key = { it.playerRef }
                    ) { player ->
                        AppSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(),
                            shape = AppShapes.list,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(playerDirectoryDotColor(player))
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            player.gameId,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (player.self) {
                                            Text(
                                                "我",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (player.followed) {
                                            Text(
                                                "已关心",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                    }
                                    Text(
                                        playerDirectoryPrimaryLabel(player),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        playerDirectoryStatusLabel(player),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { onInspect(player) }) {
                                    Text("查看")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun playerDirectoryPrimaryLabel(player: PlayerDirectoryItem): String =
    when {
        player.serverOnline && player.registered -> "服务器在线，已注册 App"
        player.serverOnline -> "服务器在线玩家"
        player.registered -> "已注册 App"
        else -> "玩家"
    }

private fun playerDirectoryStatusLabel(player: PlayerDirectoryItem): String =
    when {
        player.serverOnline -> "当前服务器在线"
        player.appForeground -> "App 前台在线"
        player.appConnected -> "App 后台连接在线"
        player.appStatus == "online" -> "App 前台在线"
        player.appStatus == "just_online" -> "App 5 分钟内在线"
        player.appStatus == "recent_online" -> "App 30 分钟内在线"
        player.appStatus == "recently_online" -> "App 1 天内在线"
        player.appLastSeenAt != null -> "App 离线较久"
        else -> "暂无 App 在线记录"
    }

private fun playerDirectoryDotColor(player: PlayerDirectoryItem): Color =
    when {
        player.serverOnline -> Color(0xFF2E7D32)
        player.appForeground || player.appConnected -> Color(0xFF1565C0)
        player.appStatus == "just_online" || player.appStatus == "recent_online" -> Color(0xFFF9A825)
        else -> Color(0xFF9E9E9E)
    }

@Composable
private fun MentionSuggestions(
    players: List<PlayerDirectoryItem>,
    onSelect: (PlayerDirectoryItem) -> Unit
) {
    AppSurface(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth(),
        shape = AppShapes.list
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "选择要提及的玩家",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${players.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = players,
                    key = { it.playerRef }
                ) { player ->
                    AppSurface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(),
                        shape = AppShapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        TextButton(
                            onClick = { onSelect(player) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(playerDirectoryDotColor(player))
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text(
                                        "@${player.gameId}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        when {
                                            player.followed -> "已关心"
                                            player.serverOnline -> "服务器在线玩家"
                                            player.appForeground -> "App 前台在线"
                                            player.appConnected -> "App 后台连接在线"
                                            else -> "可提及玩家"
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
private fun ChatInputBar(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    onMentionClick: () -> Unit,
    onSend: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    fun focusInput() {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AppSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIconButtonSurface(
                modifier = Modifier.size(46.dp),
                onClick = onMentionClick
            ) {
                Icon(
                    Icons.Filled.AlternateEmail,
                    contentDescription = "提及玩家",
                    modifier = Modifier.size(24.dp)
                )
            }
            AppSurface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 46.dp, max = 108.dp)
                    .animateContentSize(animationSpec = tween(AppMotion.Fast, easing = AppMotion.Easing)),
                shape = AppShapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) { innerTextField ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (input.text.isBlank()) {
                            Text(
                                "公共聊天",
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Clip,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            }
            AppIconButtonSurface(
                modifier = Modifier.size(46.dp),
                selected = true,
                onClick = onSend
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "发送",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    user: UserProfile,
    repository: AuthRepository,
    chatRepository: ChatRepository,
    appUpdateRepository: AppUpdateRepository,
    appearance: AppAppearance,
    onThemeModeChange: (DeuteriumThemeMode) -> Unit,
    onColorPresetChange: (DeuteriumColorPreset) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    showServerEvents: Boolean,
    onShowServerEventsChange: (Boolean) -> Unit,
    walletNotifications: Boolean,
    onWalletNotificationsChange: (Boolean) -> Unit,
    onLogout: () -> Unit
) {
    var showPasswordReset by remember { mutableStateOf(false) }
    var showChatHistory by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateMessageAutoDismiss by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("我的资料", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(user.gameId, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                InfoRow("QQ", user.qq)
                InfoRow("玩家身份", "已绑定服务器身份")
            }
        }
        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("账号安全", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "修改密码会重新发送游戏内验证码。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showPasswordReset = !showPasswordReset }) {
                        Text(if (showPasswordReset) "收起" else "修改密码")
                    }
                }
                AnimatedVisibility(
                    visible = showPasswordReset,
                    enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
                        expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
                    exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
                        shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing))
                ) {
                    ResetPasswordForm(
                        repository = repository,
                        initialAccount = user.gameId,
                        accountReadOnly = true
                    ) {
                        showPasswordReset = false
                        onLogout()
                    }
                }
            }
        }
        AppearanceSettingsSection(
            appearance = appearance,
            onThemeModeChange = onThemeModeChange,
            onColorPresetChange = onColorPresetChange,
            onDynamicColorChange = onDynamicColorChange
        )
        ChatSettingsSection(
            showServerEvents = showServerEvents,
            onShowServerEventsChange = onShowServerEventsChange,
            walletNotifications = walletNotifications,
            onWalletNotificationsChange = onWalletNotificationsChange
        )
        ChatHistoryEntry(onClick = { showChatHistory = true })
        UpdateCheckEntry(
            loading = checkingUpdate,
            message = updateMessage,
            autoDismiss = updateMessageAutoDismiss,
            onDismissMessage = {
                updateMessage = null
                updateMessageAutoDismiss = false
            },
            onClick = {
                if (!checkingUpdate) {
                    checkingUpdate = true
                    updateMessage = null
                    updateMessageAutoDismiss = false
                    scope.launch {
                        when (val result = appUpdateRepository.checkUpdate()) {
                            is RepoResult.Success -> {
                                updateMessage = result.value.message
                                updateMessageAutoDismiss = true
                            }
                            is RepoResult.Error -> {
                                updateMessage = result.message
                                updateMessageAutoDismiss = false
                            }
                        }
                        checkingUpdate = false
                    }
                }
            }
        )
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.ExitToApp, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("退出登录")
        }
    }

    if (showChatHistory) {
        ChatHistoryDialog(
            repository = chatRepository,
            onDismiss = { showChatHistory = false }
        )
    }
}

@Composable
private fun UpdateCheckEntry(
    loading: Boolean,
    message: String?,
    autoDismiss: Boolean,
    onDismissMessage: () -> Unit,
    onClick: () -> Unit
) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick),
        shape = AppShapes.large
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("检查更新", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onClick,
                    enabled = !loading
                ) {
                    Text(if (loading) "检查中" else "检查")
                }
            }
            InlineMessage(
                message = message,
                autoDismiss = autoDismiss,
                onDismiss = onDismissMessage
            )
        }
    }
}

@Composable
private fun ChatHistoryEntry(onClick: () -> Unit) {
    AppSurface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = AppShapes.large
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Chat, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("聊天记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "查找本机保存的聊天历史，或删除当前账号的本地聊天记录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ChatHistoryDialog(
    repository: ChatRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var history by remember { mutableStateOf<List<ChatHistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        loading = true
        history = repository.searchHistory(query)
        loading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("聊天记录", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "仅保存并搜索当前账号在本机的聊天历史",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭聊天记录")
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜索玩家或内容") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                InlineMessage(message)

                when {
                    loading -> {
                        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.medium) {
                            Text(
                                "正在读取本地聊天记录...",
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    history.isEmpty() -> {
                        AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.medium) {
                            Text(
                                if (query.isBlank()) "本机还没有保存聊天记录。" else "没有找到匹配的聊天记录。",
                                modifier = Modifier.padding(14.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(
                                items = history,
                                key = { it.messageId }
                            ) { item ->
                                ChatHistoryRow(item, Modifier.animateItem())
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { confirmDelete = true },
                    enabled = history.isNotEmpty() || query.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除当前账号的所有聊天记录")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除聊天记录") },
            text = { Text("这只会删除当前账号保存在本机的聊天记录，不会影响后端聊天消息。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.deleteHistory()
                            history = emptyList()
                            message = "已删除当前账号的本地聊天记录。"
                            confirmDelete = false
                        }
                    }
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ChatHistoryRow(item: ChatHistoryItem, modifier: Modifier = Modifier) {
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.list,
        color = if (item.event) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (item.event) "服务器事件" else item.sender,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.displayTime.ifBlank { item.sentAt },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(item.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsSection(
    appearance: AppAppearance,
    onThemeModeChange: (DeuteriumThemeMode) -> Unit,
    onColorPresetChange: (DeuteriumColorPreset) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit
) {
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("应用外观", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "外观设置会保存在当前设备。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text("主题模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeuteriumThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = appearance.themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.label) }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("莫奈动态取色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (dynamicAvailable) "使用系统壁纸色生成 Material 3 配色。" else "需要 Android 12 或更高版本。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appearance.dynamicColor && dynamicAvailable,
                    enabled = dynamicAvailable,
                    onCheckedChange = onDynamicColorChange
                )
            }

            Text("应用颜色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DeuteriumColorPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = appearance.colorPreset == preset && !appearance.dynamicColor,
                        enabled = !appearance.dynamicColor,
                        onClick = { onColorPresetChange(preset) },
                        label = { Text(preset.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(preset.swatch)
                            )
                        }
                    )
                }
            }

        }
    }
}

@Composable
private fun ChatSettingsSection(
    showServerEvents: Boolean,
    onShowServerEventsChange: (Boolean) -> Unit,
    walletNotifications: Boolean,
    onWalletNotificationsChange: (Boolean) -> Unit
) {
    AppSurface(modifier = Modifier.fillMaxWidth(), shape = AppShapes.large) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SettingSwitchRow(
                title = "服务器事件",
                description = "显示服务器事件，例如玩家进出、签到和世界保存提示。",
                checked = showServerEvents,
                onCheckedChange = onShowServerEventsChange
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            SettingSwitchRow(
                title = "钱包变动通知",
                description = "App 在后台时，成功收入或支出会通过系统通知提醒。",
                checked = walletNotifications,
                onCheckedChange = onWalletNotificationsChange
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
@Composable
private fun WalletRecordRow(record: WalletRecord, modifier: Modifier = Modifier) {
    val sign = if (record.direction == "income") "+" else "-"
    AppSurface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.list
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(record.otherPlayer.gameId, fontWeight = FontWeight.SemiBold)
                Text(
                    text = listOfNotNull(formatIsoDateTimeUtc8(record.occurredAt), record.status, record.note).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$sign${record.amount}",
                fontWeight = FontWeight.Bold,
                color = if (record.direction == "income") Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSummaryCard(player: PlayerSummary) {
    AppSurface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = AppShapes.medium
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(player.gameId, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = player.online, onClick = {}, label = { Text(if (player.online) "在线" else "离线") })
                player.qq?.let { FilterChip(selected = false, onClick = {}, label = { Text("QQ $it") }) }
            }
            Text(if (player.registered) "已绑定 App 账号" else "服务器可确认玩家", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatFeedItem,
    modifier: Modifier = Modifier,
    onLongCopy: () -> Unit
) {
    if (message.event) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = AppShapes.pill,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClickLabel = "复制",
                    onLongClick = onLongCopy
                )
            ) {
                Text(
                    text = message.content,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        return
    }

    val bubbleShape = RoundedCornerShape(22.dp)
    val bubbleColor = if (message.mine) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (message.mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            contentColor = contentColor,
            shape = bubbleShape,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .widthIn(max = 340.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClickLabel = "复制",
                    onLongClick = onLongCopy
                )
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row {
                    Text(
                        message.sender,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        color = contentColor
                    )
                    Text(
                        message.time,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.72f)
                    )
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun RowScope.BottomItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BottomItemIconScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.74f,
        animationSpec = tween(AppMotion.Fast, easing = AppMotion.Easing),
        label = "BottomItemAlpha"
    )
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                    alpha = contentAlpha
                }
            )
        },
        label = { Text(label) }
    )
}

@Composable
private fun PrimaryActionButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) AppMotion.PressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "PrimaryActionPressScale"
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = AppShapes.pill,
        interactionSource = interactionSource
    ) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}

@Composable
private fun InlineMessage(
    message: String?,
    modifier: Modifier = Modifier,
    autoDismiss: Boolean = false,
    onDismiss: () -> Unit = {}
) {
    LaunchedEffect(message, autoDismiss) {
        if (message != null && autoDismiss) {
            delay(3_000)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(tween(AppMotion.Fast, easing = AppMotion.Easing)) +
            expandVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
        exit = fadeOut(tween(AppMotion.Micro, easing = AppMotion.Easing)) +
            shrinkVertically(tween(AppMotion.Fast, easing = AppMotion.Easing)),
        modifier = modifier.fillMaxWidth()
    ) {
        if (message != null) {
            AppSurface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = AppShapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun activeMentionQuery(input: TextFieldValue): String? {
    val cursor = input.selection.end.coerceIn(0, input.text.length)
    val beforeCursor = input.text.substring(0, cursor)
    val atIndex = beforeCursor.lastIndexOf('@')
    if (atIndex == -1) return null
    val query = beforeCursor.substring(atIndex + 1)
    return if (query.any { it.isWhitespace() }) null else query
}

private fun appendMentionMarker(input: TextFieldValue): TextFieldValue {
    val start = minOf(input.selection.start, input.selection.end).coerceIn(0, input.text.length)
    val end = maxOf(input.selection.start, input.selection.end).coerceIn(0, input.text.length)
    val before = input.text.substring(0, start)
    val after = input.text.substring(end)
    val prefix = when {
        before.isBlank() -> ""
        before.endsWith(" ") -> before
        else -> "$before "
    }
    val text = "${prefix}@${after}"
    val cursor = prefix.length + 1
    return TextFieldValue(text, TextRange(cursor))
}

private fun insertMention(input: TextFieldValue, gameId: String): TextFieldValue {
    val query = activeMentionQuery(input)
    val selectionStart = minOf(input.selection.start, input.selection.end).coerceIn(0, input.text.length)
    val selectionEnd = maxOf(input.selection.start, input.selection.end).coerceIn(0, input.text.length)
    val beforeSelection = input.text.substring(0, selectionStart)
    val afterSelection = input.text.substring(selectionEnd)
    val atIndex = beforeSelection.lastIndexOf('@')
    val mention = "@$gameId "
    val text: String
    val cursor: Int
    if (query != null && atIndex != -1) {
        val prefix = beforeSelection.substring(0, atIndex)
        text = prefix + mention + afterSelection
        cursor = prefix.length + mention.length
    } else if (beforeSelection.isBlank()) {
        text = mention + afterSelection
        cursor = mention.length
    } else {
        val prefix = if (beforeSelection.endsWith(" ")) beforeSelection else "$beforeSelection "
        text = prefix + mention + afterSelection
        cursor = prefix.length + mention.length
    }
    return TextFieldValue(text, TextRange(cursor))
}

