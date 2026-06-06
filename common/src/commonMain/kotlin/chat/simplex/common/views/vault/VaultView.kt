package chat.simplex.common.views.vault

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import chat.simplex.common.model.ChatController
import chat.simplex.common.platform.BackHandler
import chat.simplex.common.ui.theme.DMSans
import chat.simplex.common.views.helpers.LAResult
import chat.simplex.common.views.helpers.authenticate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BgPrimary @Composable get() = MaterialTheme.colors.background
private val CardBg @Composable get() = MaterialTheme.colors.surface
private val AccentBlue @Composable get() = MaterialTheme.colors.primary
private val TextPrimary @Composable get() = MaterialTheme.colors.onBackground
private val TextSecondary @Composable get() = MaterialTheme.colors.secondary
private val DangerRed @Composable get() = MaterialTheme.colors.error
private val GreenBadge = Color(0xFF4CAF50)
private val AmberBadge = Color(0xFFFF9800)

// Brand gradient used across the vault for a cohesive, modern look.
private val VaultGradientStart = Color(0xFF3A1C71)
private val VaultGradientMid = Color(0xFF2541B2)
private val VaultGradientEnd = Color(0xFF1F8EF1)
private val VaultGradient = Brush.linearGradient(
    listOf(VaultGradientStart, VaultGradientMid, VaultGradientEnd)
)

private sealed class VScreen {
    object Locked : VScreen()
    object Home : VScreen()
    data class Folder(val folder: VaultFolder, val password: String? = null) : VScreen()
    data class Preview(val entry: VaultFileEntry, val folderPassword: String? = null) : VScreen()
    data class BackupSettings(val startRestore: Boolean = false) : VScreen()
}

@Composable
fun VaultView(close: () -> Unit) {
    var screen by remember { mutableStateOf<VScreen>(VScreen.Locked) }
    var index by remember { mutableStateOf(VaultIndex()) }
    val scope = rememberCoroutineScope()

    fun reload() { scope.launch { index = withContext(Dispatchers.IO) { VaultStorage.getIndex() } } }

    // Back navigation
    BackHandler(enabled = screen is VScreen.Preview) {
        val prev = screen as VScreen.Preview
        val folder = index.folders.find { it.id == prev.entry.folderId }
        screen = if (folder != null) VScreen.Folder(folder, prev.folderPassword) else VScreen.Home
    }
    BackHandler(enabled = screen is VScreen.Folder) { screen = VScreen.Home }
    BackHandler(enabled = screen is VScreen.BackupSettings) { screen = VScreen.Home }
    BackHandler(enabled = screen == VScreen.Home) { close() }
    BackHandler(enabled = screen == VScreen.Locked) { close() }

    // Authenticate on entry
    LaunchedEffect(Unit) {
        if (ChatController.appPrefs.performLA.get()) {
            authenticate(
                promptTitle = "Unlock Vault",
                promptSubtitle = "Authenticate to access your secure vault",
                oneTime = true
            ) { result ->
                when (result) {
                    is LAResult.Success -> { screen = VScreen.Home; reload() }
                    else -> close()
                }
            }
        } else {
            screen = VScreen.Home; reload()
        }
    }

    when (val s = screen) {
        VScreen.Locked -> LockedScreen()
        VScreen.Home -> HomeScreen(
            index = index,
            onOpenFolder = { folder ->
                if (folder.hasPassword) {
                    screen = VScreen.Locked
                } else {
                    screen = VScreen.Folder(folder, null)
                }
            },
            onFolderVerified = { folder, pw -> screen = VScreen.Folder(folder, pw) },
            onFileClick = { entry -> screen = VScreen.Preview(entry) },
            onRefresh = { reload() },
            onBack = close,
            onBackupSettings = { screen = VScreen.BackupSettings() },
            onRestore = { screen = VScreen.BackupSettings(startRestore = true) }
        )
        is VScreen.Folder -> FolderScreen(
            folder = s.folder,
            folderPassword = s.password,
            files = index.files.filter { it.folderId == s.folder.id },
            onFileClick = { entry -> screen = VScreen.Preview(entry, s.password) },
            onRefresh = { reload() },
            onBack = { screen = VScreen.Home }
        )
        is VScreen.Preview -> PreviewScreen(
            entry = s.entry,
            folderPassword = s.folderPassword,
            onBack = {
                val folder = index.folders.find { it.id == s.entry.folderId }
                screen = if (folder != null) VScreen.Folder(folder, s.folderPassword) else VScreen.Home
            },
            onDeleted = {
                val folder = index.folders.find { it.id == s.entry.folderId }
                screen = if (folder != null) VScreen.Folder(folder, s.folderPassword) else VScreen.Home
                reload()
            }
        )
        is VScreen.BackupSettings -> BackupSettingsView(
            startRestore = s.startRestore,
            onBack = { screen = VScreen.Home; reload() }
        )
    }
}

// ═══════════════ LOCKED ═══════════════

@Composable
private fun LockedScreen() {
    val infinite = rememberInfiniteTransition()
    val glow by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(
                    VaultGradientStart.copy(alpha = 0.10f),
                    BgPrimary,
                    VaultGradientEnd.copy(alpha = 0.06f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(132.dp).clip(CircleShape)
                        .background(VaultGradientEnd.copy(alpha = glow * 0.25f))
                )
                Box(
                    Modifier.size(96.dp).clip(CircleShape).background(VaultGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(46.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.height(28.dp))
            Text("Secure Vault", fontSize = 24.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(14.dp), tint = TextSecondary)
                Spacer(Modifier.width(6.dp))
                Text("Authenticating…", fontSize = 14.sp, fontFamily = DMSans, color = TextSecondary)
            }
        }
    }
}

// ═══════════════ HOME ═══════════════

@Composable
private fun HomeScreen(
    index: VaultIndex,
    onOpenFolder: (VaultFolder) -> Unit,
    onFolderVerified: (VaultFolder, String?) -> Unit,
    onFileClick: (VaultFileEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onBackupSettings: () -> Unit = {},
    onRestore: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val folders = index.folders
    val looseFiles = index.files.filter { it.folderId == null }
    var showCreateFolder by remember { mutableStateOf(false) }
    var pickerSource by remember { mutableStateOf<VaultPickSource?>(null) }
    var passwordPrompt by remember { mutableStateOf<VaultFolder?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var deleteFolder by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFolderAuthPending by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFolderPasswordPending by remember { mutableStateOf<VaultFolder?>(null) }
    var deleteFile by remember { mutableStateOf<VaultFileEntry?>(null) }

    val backupEnabled = remember { mutableStateOf(VaultBackup.isBackupEnabled()) }
    val isPremiumUser = ChatController.appPrefs.premiumActive.state
    LaunchedEffect(Unit) { backupEnabled.value = VaultBackup.isBackupEnabled() }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            VaultTopBar(
                "Secure Vault",
                onBack = onBack,
                trailing = {
                    // Cloud backup is a Pro-tier feature — gate the entry point.
                    IconButton(onClick = {
                        chat.simplex.common.views.chatlist.PremiumGate.requirePremium {
                            onBackupSettings()
                        }
                    }) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            val iconTint = when {
                                !isPremiumUser.value -> MaterialTheme.colors.secondary
                                else -> TextPrimary
                            }
                            Icon(Icons.Filled.Settings, "Backup Settings", Modifier.size(22.dp), tint = iconTint)
                            // Small status dot: green when cloud backup is active.
                            if (isPremiumUser.value && backupEnabled.value) {
                                Box(
                                    Modifier.size(8.dp).clip(CircleShape)
                                        .background(GreenBadge)
                                        .align(Alignment.TopEnd)
                                )
                            }
                        }
                    }
                }
            )

            if (folders.isEmpty() && looseFiles.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(Modifier.size(124.dp).clip(CircleShape).background(VaultGradientEnd.copy(alpha = 0.08f)))
                            Box(
                                Modifier.size(88.dp).clip(CircleShape).background(VaultGradient),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Shield, null, Modifier.size(42.dp), tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Your vault is empty", fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Add files and create folders.\nEverything is encrypted with AES-256-GCM and hardware-backed keys.",
                            fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary, textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = { showCreateFolder = true },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New Folder", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Box(
                                Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(VaultGradient)
                                    .clickable { pickerSource = VaultPickSource.FILES }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Add File", color = Color.White, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Restore shortcut — discoverable on a fresh install so users
                        // can pull their cloud backup without hunting in settings.
                        TextButton(onClick = {
                            chat.simplex.common.views.chatlist.PremiumGate.requirePremium { onRestore() }
                        }) {
                            Icon(Icons.Filled.CloudDownload, null, Modifier.size(18.dp), tint = AccentBlue)
                            Spacer(Modifier.width(6.dp))
                            Text("Restore from Cloud", fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AccentBlue)
                        }
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            VaultHeroHeader(
                                fileCount = index.files.size,
                                folderCount = folders.size,
                                totalBytes = index.files.sumOf { it.sizeBytes },
                                backupEnabled = backupEnabled.value,
                                isPremium = isPremiumUser.value
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    // Folders section (full-width rows)
                    if (folders.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("Folders", folders.size)
                        }
                        items(folders, span = { GridItemSpan(maxLineSpan) }, key = { it.id }) { folder ->
                            val count = index.files.count { it.folderId == folder.id }
                            FolderCard(
                                folder = folder,
                                fileCount = count,
                                onClick = {
                                    if (folder.hasPassword) passwordPrompt = folder
                                    else onFolderVerified(folder, null)
                                },
                                onDelete = { deleteFolderAuthPending = folder }
                            )
                        }
                    }
                    // Loose files section (thumbnail tiles)
                    if (looseFiles.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            SectionHeader("Files", looseFiles.size)
                        }
                        items(looseFiles, key = { it.id }) { entry ->
                            FileTile(
                                entry = entry,
                                folderPassword = null,
                                onClick = { onFileClick(entry) },
                                onDelete = { deleteFile = entry }
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }
            }
        }

        // FAB with menu
        if (folders.isNotEmpty() || looseFiles.isNotEmpty()) {
            Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 90.dp)) {
                Box(
                    Modifier.size(58.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                        .background(VaultGradient).clickable { showAddMenu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Add, "Add", Modifier.size(28.dp), tint = Color.White)
                }
                DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                    DropdownMenuItem(onClick = { showAddMenu = false; showCreateFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("New Folder", fontFamily = DMSans)
                    }
                    DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.FILES }) {
                        Icon(Icons.Filled.NoteAdd, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Add File", fontFamily = DMSans)
                    }
                    DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.CAMERA_PHOTO }) {
                        Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Take Photo", fontFamily = DMSans)
                    }
                    DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.CAMERA_VIDEO }) {
                        Icon(Icons.Filled.Videocam, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Record Video", fontFamily = DMSans)
                    }
                    Divider()
                    DropdownMenuItem(onClick = {
                        showAddMenu = false
                        chat.simplex.common.views.chatlist.PremiumGate.requirePremium { onRestore() }
                    }) {
                        Icon(Icons.Filled.CloudDownload, null, Modifier.size(20.dp), tint = AccentBlue)
                        Spacer(Modifier.width(12.dp))
                        Text("Restore from Cloud", fontFamily = DMSans, color = AccentBlue)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolder) {
        CreateFolderDialog(
            onDismiss = { showCreateFolder = false },
            onCreate = { name, pw ->
                showCreateFolder = false
                scope.launch { withContext(Dispatchers.IO) { VaultStorage.createFolder(name, pw) }; onRefresh() }
            }
        )
    }

    passwordPrompt?.let { folder ->
        FolderPasswordDialog(
            folderName = folder.name,
            onDismiss = { passwordPrompt = null },
            onSubmit = { pw ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { VaultStorage.verifyFolderPassword(folder.id, pw) }
                    if (ok) { passwordPrompt = null; onFolderVerified(folder, pw) }
                }
            }
        )
    }

    // Step 1: App lock authentication before folder delete
    deleteFolderAuthPending?.let { folder ->
        LaunchedEffect(folder.id) {
            if (ChatController.appPrefs.performLA.get()) {
                authenticate(
                    promptTitle = "Delete Folder",
                    promptSubtitle = "Authenticate to delete \"${folder.name}\"",
                    oneTime = true
                ) { result ->
                    deleteFolderAuthPending = null
                    when (result) {
                        is LAResult.Success -> {
                            if (folder.hasPassword) deleteFolderPasswordPending = folder
                            else deleteFolder = folder
                        }
                        else -> {}
                    }
                }
            } else {
                deleteFolderAuthPending = null
                if (folder.hasPassword) deleteFolderPasswordPending = folder
                else deleteFolder = folder
            }
        }
    }

    // Step 2: Folder password verification (for password-protected folders)
    deleteFolderPasswordPending?.let { folder ->
        FolderPasswordDialog(
            folderName = folder.name,
            onDismiss = { deleteFolderPasswordPending = null },
            onSubmit = { pw ->
                scope.launch {
                    val ok = withContext(Dispatchers.IO) { VaultStorage.verifyFolderPassword(folder.id, pw) }
                    if (ok) {
                        deleteFolderPasswordPending = null
                        deleteFolder = folder
                    }
                }
            }
        )
    }

    // Step 3: Final delete confirmation
    deleteFolder?.let { folder ->
        DeleteConfirmDialog(
            title = "Delete Folder",
            message = "\"${folder.name}\" and all its files will be permanently deleted. This cannot be undone.",
            onDismiss = { deleteFolder = null },
            onConfirm = {
                deleteFolder = null
                scope.launch { withContext(Dispatchers.IO) { VaultStorage.deleteFolder(folder.id) }; onRefresh() }
            }
        )
    }

    deleteFile?.let { entry ->
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { deleteFile = null },
            onConfirm = {
                deleteFile = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onRefresh()
                }
            }
        )
    }

    pickerSource?.let { src ->
        VaultFilePicker(
            source = src,
            onFilePicked = { name, mime, bytes ->
                pickerSource = null
                scope.launch {
                    val entry = withContext(Dispatchers.IO) { VaultStorage.importFile(name, mime, bytes, null, null) }
                    onRefresh()
                    if (entry != null && VaultBackup.isBackupEnabled()) {
                        withContext(Dispatchers.IO) { VaultBackup.backupFile(entry) }
                        onRefresh()
                    }
                }
            },
            onDismiss = { pickerSource = null }
        )
    }
}

// ═══════════════ FOLDER CONTENTS ═══════════════

@Composable
private fun FolderScreen(
    folder: VaultFolder,
    folderPassword: String?,
    files: List<VaultFileEntry>,
    onFileClick: (VaultFileEntry) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var pickerSource by remember { mutableStateOf<VaultPickSource?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var deleteFile by remember { mutableStateOf<VaultFileEntry?>(null) }

    Column(Modifier.fillMaxSize().background(BgPrimary)) {
        VaultTopBar(
            title = folder.name,
            onBack = onBack,
            showLock = folder.hasPassword,
            subtitle = "${files.size} file${if (files.size != 1) "s" else ""}"
        )

        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(84.dp).clip(CircleShape).background(AccentBlue.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FolderOpen, null, Modifier.size(40.dp), tint = AccentBlue)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Folder is empty", fontSize = 17.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Spacer(Modifier.height(20.dp))
                    Box(
                        Modifier.height(48.dp).clip(RoundedCornerShape(14.dp)).background(VaultGradient)
                            .clickable { pickerSource = VaultPickSource.FILES }.padding(horizontal = 22.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add File", color = Color.White, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        } else {
            Box(Modifier.weight(1f)) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(files, key = { it.id }) { entry ->
                        FileTile(
                            entry = entry,
                            folderPassword = folderPassword,
                            onClick = { onFileClick(entry) },
                            onDelete = { deleteFile = entry }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(80.dp)) }
                }

                // FAB with add menu
                Box(Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 24.dp)) {
                    Box(
                        Modifier.size(58.dp).shadow(10.dp, CircleShape).clip(CircleShape)
                            .background(VaultGradient).clickable { showAddMenu = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Filled.Add, "Add", Modifier.size(28.dp), tint = Color.White) }
                    DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                        DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.FILES }) {
                            Icon(Icons.Filled.NoteAdd, null, Modifier.size(20.dp), tint = TextPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Add File", fontFamily = DMSans)
                        }
                        DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.CAMERA_PHOTO }) {
                            Icon(Icons.Filled.PhotoCamera, null, Modifier.size(20.dp), tint = TextPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Take Photo", fontFamily = DMSans)
                        }
                        DropdownMenuItem(onClick = { showAddMenu = false; pickerSource = VaultPickSource.CAMERA_VIDEO }) {
                            Icon(Icons.Filled.Videocam, null, Modifier.size(20.dp), tint = TextPrimary)
                            Spacer(Modifier.width(12.dp))
                            Text("Record Video", fontFamily = DMSans)
                        }
                    }
                }
            }
        }
    }

    deleteFile?.let { entry ->
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { deleteFile = null },
            onConfirm = {
                deleteFile = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onRefresh()
                }
            }
        )
    }

    pickerSource?.let { src ->
        VaultFilePicker(
            source = src,
            onFilePicked = { name, mime, bytes ->
                pickerSource = null
                scope.launch {
                    val entry = withContext(Dispatchers.IO) { VaultStorage.importFile(name, mime, bytes, folder.id, folderPassword) }
                    onRefresh()
                    if (entry != null && VaultBackup.isBackupEnabled()) {
                        withContext(Dispatchers.IO) { VaultBackup.backupFileWithPassword(entry, folderPassword) }
                        onRefresh()
                    }
                }
            },
            onDismiss = { pickerSource = null }
        )
    }
}

// ═══════════════ FILE PREVIEW ═══════════════

@Composable
private fun PreviewScreen(
    entry: VaultFileEntry,
    folderPassword: String?,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showDelete by remember { mutableStateOf(false) }
    var showRestore by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var tempFilePath by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(entry.id) {
        bytes = withContext(Dispatchers.IO) { VaultStorage.decryptFile(entry, folderPassword) }
        loading = false
    }

    val isPdf = entry.mimeType.contains("pdf", ignoreCase = true) ||
        entry.originalName.endsWith(".pdf", ignoreCase = true)

    val mediaPath = remember(bytes) {
        val data = bytes ?: return@remember null
        if (entry.fileType == VaultFileType.VIDEO || entry.fileType == VaultFileType.AUDIO) {
            val ext = when {
                entry.mimeType.contains("mp4") -> "mp4"
                entry.mimeType.contains("webm") -> "webm"
                entry.mimeType.contains("3gp") -> "3gp"
                entry.mimeType.contains("mkv") -> "mkv"
                entry.mimeType.contains("mp3") -> "mp3"
                entry.mimeType.contains("ogg") -> "ogg"
                entry.mimeType.contains("wav") -> "wav"
                entry.fileType == VaultFileType.VIDEO -> "mp4"
                else -> "mp3"
            }
            writeVaultTempFile(data, ext)?.also { tempFilePath = it }
        } else if (isPdf) {
            writeVaultTempFile(data, "pdf")?.also { tempFilePath = it }
        } else null
    }

    DisposableEffect(Unit) {
        onDispose {
            tempFilePath?.let { cleanupVaultTempFile(it) }
        }
    }

    fun doBack() {
        tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
        onBack()
    }

    val isFullMedia = entry.fileType == VaultFileType.VIDEO

    Column(Modifier.fillMaxSize().background(if (isFullMedia) Color.Black else BgPrimary)) {
        if (!isFullMedia) {
            Surface(elevation = 2.dp, color = CardBg) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { doBack() }) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${formatSize(entry.sizeBytes)} · ${entry.fileType.name}", fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                    }
                    IconButton(onClick = { showRestore = true }) {
                        Icon(Icons.Filled.SaveAlt, "Restore to Phone", tint = AccentBlue)
                    }
                    IconButton(onClick = { showDelete = true }) {
                        Icon(Icons.Filled.Delete, "Delete", tint = DangerRed)
                    }
                }
            }
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(color = AccentBlue)
            } else if (bytes != null) {
                val data = bytes!!
                val imageBmp = remember(data) {
                    if (entry.fileType == VaultFileType.PHOTO) decodeVaultImage(data) else null
                }

                when {
                    imageBmp != null -> {
                        Image(
                            bitmap = imageBmp,
                            contentDescription = entry.originalName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    mediaPath != null && entry.fileType == VaultFileType.VIDEO -> {
                        VaultMediaPlayer(filePath = mediaPath, isVideo = true)
                    }
                    mediaPath != null && entry.fileType == VaultFileType.AUDIO -> {
                        Column(
                            Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                Modifier.size(100.dp).clip(CircleShape).background(Color(0xFFFF9800).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.MusicNote, null, Modifier.size(48.dp), tint = Color(0xFFFF9800))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(formatSize(entry.sizeBytes), fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                            Spacer(Modifier.height(24.dp))
                            VaultMediaPlayer(filePath = mediaPath, isVideo = false)
                        }
                    }
                    isPdf && mediaPath != null -> {
                        VaultPdfViewer(
                            filePath = mediaPath,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    else -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(100.dp).clip(CircleShape).background(fileTypeColor(entry.fileType).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(fileTypeIcon(entry.fileType), null, Modifier.size(48.dp), tint = fileTypeColor(entry.fileType))
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(entry.originalName, fontSize = 16.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(4.dp))
                            Text(formatSize(entry.sizeBytes) + " · " + formatDate(entry.addedAtMs), fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                        }
                    }
                }

                if (isFullMedia) {
                    Row(
                        Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { doBack() }) {
                            Icon(Icons.Filled.ArrowBack, "Back", Modifier.size(24.dp), tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(entry.originalName, fontSize = 14.sp, fontFamily = DMSans, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showRestore = true }) {
                            Icon(Icons.Filled.SaveAlt, "Restore", Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.8f))
                        }
                        IconButton(onClick = { showDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete", Modifier.size(22.dp), tint = Color.White.copy(alpha = 0.8f))
                        }
                    }
                }
            } else {
                Text("Failed to decrypt file", fontSize = 16.sp, fontFamily = DMSans, color = DangerRed)
            }
        }
    }

    if (showRestore) {
        AlertDialog(
            onDismissRequest = { if (!restoring) showRestore = false },
            title = { Text("Restore to Phone", fontFamily = DMSans, fontWeight = FontWeight.Bold) },
            text = {
                if (restoring) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = AccentBlue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Saving to phone and cleaning up...", fontFamily = DMSans, fontSize = 14.sp, color = TextSecondary)
                    }
                } else {
                    Text(
                        "\"${entry.originalName}\" will be saved back to your phone storage. " +
                            "It will be removed from the vault" +
                            if (entry.backedUp) " and from cloud backup." else ".",
                        fontFamily = DMSans, fontSize = 14.sp
                    )
                }
            },
            confirmButton = {
                if (!restoring) {
                    TextButton(onClick = {
                        restoring = true
                        scope.launch {
                            val data = bytes
                            if (data != null) {
                                val saved = withContext(Dispatchers.IO) {
                                    restoreFileToDevice(data, entry.originalName, entry.mimeType)
                                }
                                if (saved) {
                                    withContext(Dispatchers.IO) {
                                        VaultStorage.deleteFile(entry)
                                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                                    }
                                    tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
                                    showRestore = false
                                    restoring = false
                                    onDeleted()
                                } else {
                                    restoring = false
                                }
                            } else {
                                restoring = false
                            }
                        }
                    }) {
                        Text("Restore", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            },
            dismissButton = {
                if (!restoring) {
                    TextButton(onClick = { showRestore = false }) {
                        Text("Cancel", fontFamily = DMSans, color = TextSecondary)
                    }
                }
            }
        )
    }

    if (showDelete) {
        DeleteConfirmDialog(
            title = "Delete File",
            message = "\"${entry.originalName}\" will be permanently deleted.",
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                tempFilePath?.let { cleanupVaultTempFile(it); tempFilePath = null }
                scope.launch {
                    withContext(Dispatchers.IO) {
                        VaultStorage.deleteFile(entry)
                        if (entry.backedUp) VaultBackup.deleteBackup(entry.id)
                    }
                    onDeleted()
                }
            }
        )
    }
}

@Composable
expect fun VaultMediaPlayer(
    filePath: String,
    isVideo: Boolean
)

// ═══════════════ SHARED COMPONENTS ═══════════════

@Composable
private fun VaultTopBar(
    title: String,
    onBack: () -> Unit,
    showLock: Boolean = false,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Surface(elevation = 0.dp, color = CardBg) {
        Column {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.ArrowBack, "Back", Modifier.size(22.dp), tint = TextPrimary) }
                Spacer(Modifier.width(4.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showLock) {
                            Icon(Icons.Filled.Lock, null, Modifier.size(16.dp), tint = AmberBadge)
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(title, fontSize = 20.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (subtitle != null) {
                        Text(subtitle, fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                    }
                }
                trailing?.invoke()
                Row(
                    Modifier.clip(CircleShape).background(GreenBadge.copy(alpha = 0.12f)).padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Lock, "Encrypted", Modifier.size(13.dp), tint = GreenBadge)
                    Spacer(Modifier.width(4.dp))
                    Text("AES-256", fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = GreenBadge)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colors.onSurface.copy(alpha = 0.06f)))
        }
    }
}

@Composable
private fun FolderCard(folder: VaultFolder, fileCount: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    val accent = if (folder.hasPassword) AmberBadge else AccentBlue
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp), elevation = 0.dp, backgroundColor = CardBg,
        border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.06f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.85f), accent.copy(alpha = 0.55f)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (folder.hasPassword) Icons.Filled.FolderSpecial else Icons.Filled.Folder,
                    null, Modifier.size(24.dp), tint = Color.White
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(folder.name, fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (folder.hasPassword) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(12.dp), tint = AmberBadge)
                        Spacer(Modifier.width(4.dp))
                        Text("Locked", fontSize = 12.sp, fontFamily = DMSans, color = AmberBadge)
                        Text(" · ", fontSize = 12.sp, color = TextSecondary)
                    }
                    Text("$fileCount file${if (fileCount != 1) "s" else ""}", fontSize = 12.sp, fontFamily = DMSans, color = TextSecondary)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, Modifier.size(20.dp), tint = TextSecondary)
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), tint = MaterialTheme.colors.secondary)
            }
        }
    }
}

@Composable
private fun VaultHeroHeader(
    fileCount: Int,
    folderCount: Int,
    totalBytes: Long,
    backupEnabled: Boolean,
    isPremium: Boolean
) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(VaultGradient).padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, null, Modifier.size(24.dp), tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Secure Vault", fontSize = 18.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("AES-256 · End-to-end encrypted", fontSize = 12.sp, fontFamily = DMSans, color = Color.White.copy(alpha = 0.82f))
                }
                val (chipIcon, chipText) = when {
                    !isPremium -> Icons.Filled.Star to "Pro"
                    backupEnabled -> Icons.Filled.CloudDone to "Synced"
                    else -> Icons.Filled.CloudOff to "Local"
                }
                Row(
                    Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.18f)).padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(chipIcon, null, Modifier.size(13.dp), tint = Color.White)
                    Spacer(Modifier.width(5.dp))
                    Text(chipText, fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                HeroStat("$fileCount", "Files")
                HeroDivider()
                HeroStat("$folderCount", "Folders")
                HeroDivider()
                HeroStat(formatSize(totalBytes), "Storage")
            }
        }
    }
}

@Composable
private fun RowScope.HeroStat(value: String, label: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 18.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, fontFamily = DMSans, color = Color.White.copy(alpha = 0.78f))
    }
}

@Composable
private fun HeroDivider() {
    Box(Modifier.height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.22f)))
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 15.sp, fontFamily = DMSans, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.clip(CircleShape).background(AccentBlue.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$count", fontSize = 12.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, color = AccentBlue)
        }
    }
}

/**
 * Loads (and lazily generates) an encrypted preview thumbnail for an entry off
 * the main thread, falling back to a tinted type icon while loading or when the
 * file has no visual preview.
 */
@Composable
private fun VaultThumbnail(
    entry: VaultFileEntry,
    folderPassword: String?,
    modifier: Modifier = Modifier
) {
    val hasPreview = entry.fileType == VaultFileType.PHOTO || entry.fileType == VaultFileType.VIDEO
    var thumb by remember(entry.id) { mutableStateOf<ImageBitmap?>(null) }
    var loaded by remember(entry.id) { mutableStateOf(!hasPreview) }

    LaunchedEffect(entry.id) {
        if (hasPreview) {
            thumb = withContext(Dispatchers.IO) {
                VaultStorage.getThumbnail(entry, folderPassword)?.let { decodeVaultImage(it) }
            }
            loaded = true
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        val bmp = thumb
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = entry.originalName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (entry.fileType == VaultFileType.VIDEO) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
                Icon(Icons.Filled.PlayCircle, null, Modifier.size(34.dp), tint = Color.White.copy(alpha = 0.92f))
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(fileTypeColor(entry.fileType).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                if (hasPreview && !loaded) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = fileTypeColor(entry.fileType))
                } else {
                    Icon(fileTypeIcon(entry.fileType), null, Modifier.size(34.dp), tint = fileTypeColor(entry.fileType))
                }
            }
        }
    }
}

@Composable
private fun FileTile(
    entry: VaultFileEntry,
    folderPassword: String?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = 1.dp,
        backgroundColor = CardBg
    ) {
        Box(Modifier.fillMaxSize()) {
            VaultThumbnail(entry, folderPassword, Modifier.fillMaxSize())

            // Status badges (top-start)
            Row(
                Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (entry.backedUp) TileBadge(Icons.Filled.CloudDone, AccentBlue)
                if (entry.isDoubleEncrypted) TileBadge(Icons.Filled.Lock, Color(0xFFFF9800))
            }

            // Overflow menu (top-end)
            Box(Modifier.align(Alignment.TopEnd)) {
                Box(
                    Modifier.padding(4.dp).size(28.dp).clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.32f))
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MoreVert, "More", Modifier.size(18.dp), tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(onClick = { menuOpen = false; onClick() }) {
                        Icon(Icons.Filled.Visibility, null, Modifier.size(20.dp), tint = TextPrimary)
                        Spacer(Modifier.width(12.dp))
                        Text("Open", fontFamily = DMSans)
                    }
                    DropdownMenuItem(onClick = { menuOpen = false; onDelete() }) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(20.dp), tint = DangerRed)
                        Spacer(Modifier.width(12.dp))
                        Text("Delete", fontFamily = DMSans, color = DangerRed)
                    }
                }
            }

            // Name + size scrim (bottom)
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f))))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    entry.originalName,
                    fontSize = 11.sp, fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                    color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    formatSize(entry.sizeBytes),
                    fontSize = 10.sp, fontFamily = DMSans, color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun TileBadge(icon: ImageVector, tint: Color) {
    Box(
        Modifier.size(22.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(13.dp), tint = tint)
    }
}

// ═══════════════ DIALOGS ═══════════════

@Composable
private fun CreateFolderDialog(onDismiss: () -> Unit, onCreate: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var enablePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPw by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder", fontFamily = DMSans, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name", fontFamily = DMSans) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, null, Modifier.size(20.dp), tint = if (enablePassword) Color(0xFFFF9800) else TextSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("Password protect", fontFamily = DMSans, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                    Switch(checked = enablePassword, onCheckedChange = { enablePassword = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue))
                }
                if (enablePassword) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password", fontFamily = DMSans) },
                        singleLine = true,
                        visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPw = !showPw }) {
                                Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirmPw,
                        onValueChange = { confirmPw = it },
                        label = { Text("Confirm password", fontFamily = DMSans) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (enablePassword && password.isNotEmpty() && confirmPw.isNotEmpty() && password != confirmPw) {
                        Spacer(Modifier.height(4.dp))
                        Text("Passwords don't match", fontSize = 12.sp, fontFamily = DMSans, color = DangerRed)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "If you forget this password, files in this folder cannot be recovered.",
                        fontSize = 11.sp, fontFamily = DMSans, color = DangerRed.copy(alpha = 0.8f)
                    )
                }
            }
        },
        confirmButton = {
            val canCreate = name.isNotBlank() && (!enablePassword || (password.length >= 4 && password == confirmPw))
            TextButton(
                onClick = { onCreate(name.trim(), if (enablePassword) password else null) },
                enabled = canCreate
            ) {
                Text("Create", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = if (canCreate) AccentBlue else TextSecondary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = DMSans, color = TextSecondary) }
        }
    )
}

@Composable
private fun FolderPasswordDialog(folderName: String, onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var showPw by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Lock, null, Modifier.size(20.dp), tint = Color(0xFFFF9800))
                Spacer(Modifier.width(8.dp))
                Text(folderName, fontFamily = DMSans, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column {
                Text("Enter password to access this folder", fontSize = 13.sp, fontFamily = DMSans, color = TextSecondary)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = false },
                    label = { Text("Password", fontFamily = DMSans) },
                    singleLine = true,
                    visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
                    isError = error,
                    trailingIcon = {
                        IconButton(onClick = { showPw = !showPw }) {
                            Icon(if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = TextSecondary)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                if (error) {
                    Spacer(Modifier.height(4.dp))
                    Text("Wrong password", fontSize = 12.sp, fontFamily = DMSans, color = DangerRed)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotEmpty()) {
                        error = true
                        onSubmit(password)
                    }
                },
                enabled = password.isNotEmpty()
            ) { Text("Unlock", fontFamily = DMSans, fontWeight = FontWeight.Bold, color = AccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", fontFamily = DMSans, color = TextSecondary) }
        }
    )
}

@Composable
private fun DeleteConfirmDialog(title: String, message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = DMSans, fontWeight = FontWeight.Bold) },
        text = { Text(message, fontFamily = DMSans) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = DangerRed, fontFamily = DMSans, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary, fontFamily = DMSans) }
        }
    )
}

// ═══════════════ HELPERS ═══════════════

internal fun fileTypeIcon(type: VaultFileType) = when (type) {
    VaultFileType.PHOTO -> Icons.Filled.Image
    VaultFileType.VIDEO -> Icons.Filled.PlayCircle
    VaultFileType.AUDIO -> Icons.Filled.MusicNote
    VaultFileType.DOCUMENT -> Icons.Filled.Description
    VaultFileType.OTHER -> Icons.Filled.InsertDriveFile
}

internal fun fileTypeColor(type: VaultFileType) = when (type) {
    VaultFileType.PHOTO -> Color(0xFF4CAF50)
    VaultFileType.VIDEO -> Color(0xFF2196F3)
    VaultFileType.AUDIO -> Color(0xFFFF9800)
    VaultFileType.DOCUMENT -> Color(0xFF9C27B0)
    VaultFileType.OTHER -> Color(0xFF607D8B)
}

internal fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "${b / 1024} KB"
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / (1024.0 * 1024))
    else -> "%.2f GB".format(b / (1024.0 * 1024 * 1024))
}

private fun formatDate(ms: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ms))
}
