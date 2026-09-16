package com.Pinak.ProductiveClub.ui.drawer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.Pinak.ProductiveClub.util.FriendManager
import com.Pinak.ProductiveClub.util.NetworkHelper
import com.Pinak.ProductiveClub.util.ScreenTimeHelper
import kotlinx.coroutines.launch

enum class SidebarTab {
    Friends,
    Groups
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSidebar(
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: SidebarTab = SidebarTab.Friends
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val myFriendCode by FriendManager.myFriendCode.collectAsState()
    val friendStatuses by FriendManager.friendStatuses.collectAsState()
    val groups by FriendManager.groups.collectAsState()

    var activeTab by remember(initialTab) { mutableStateOf(initialTab) }
    var showAddDialog by remember { mutableStateOf(false) }
    var friendCodeInput by remember { mutableStateOf("") }
    var groupNameInput by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    var isAdding by remember { mutableStateOf(false) }

    var isScreenTimeShared by remember { mutableStateOf(FriendManager.isScreenTimeShared(context)) }
    val hasUsagePermission = remember(context) { ScreenTimeHelper.hasUsagePermission(context) }

    // Add Friend / Create Group Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                addError = null
            },
            title = {
                Text(if (activeTab == SidebarTab.Friends) "Add Friend" else "Create Group")
            },
            text = {
                Column {
                    if (activeTab == SidebarTab.Friends) {
                        Text(
                            "Enter your friend's unique code (e.g. PC-1234):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = friendCodeInput,
                            onValueChange = { friendCodeInput = it.uppercase() },
                            placeholder = { Text("PC-XXXX") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    } else {
                        Text(
                            "Enter a name for your group:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = groupNameInput,
                            onValueChange = { groupNameInput = it },
                            placeholder = { Text("e.g. Study Squad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    addError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!NetworkHelper.isNetworkAvailable(context)) {
                            addError = "You are offline. Turn on Wi-Fi or Mobile Data to connect."
                            return@Button
                        }
                        scope.launch {
                            isAdding = true
                            addError = null
                            if (activeTab == SidebarTab.Friends) {
                                val result = FriendManager.addFriendByCode(friendCodeInput)
                                isAdding = false
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        showAddDialog = false
                                        friendCodeInput = ""
                                    },
                                    onFailure = { addError = it.message ?: "Failed to add friend" }
                                )
                            } else {
                                val result = FriendManager.createGroup(groupNameInput, emptyList())
                                isAdding = false
                                result.fold(
                                    onSuccess = {
                                        Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                        showAddDialog = false
                                        groupNameInput = ""
                                    },
                                    onFailure = { addError = it.message ?: "Failed to create group" }
                                )
                            }
                        }
                    },
                    enabled = !isAdding && (if (activeTab == SidebarTab.Friends) friendCodeInput.isNotBlank() else groupNameInput.isNotBlank())
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (activeTab == SidebarTab.Friends) "Add" else "Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalDrawerSheet(
        modifier = modifier.width(320.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with Friend Code
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Productive Club", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onCloseDrawer) {
                    Icon(Icons.Default.Close, contentDescription = "Close Drawer")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // My Friend Code Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Your Friend Code", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = myFriendCode.ifBlank { "Loading..." },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Friend Code", myFriendCode))
                                Toast.makeText(context, "Friend code copied!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy code", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tabs: Friends vs Groups + Add Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SecondaryTabRow(
                    selectedTabIndex = activeTab.ordinal,
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = activeTab == SidebarTab.Friends,
                        onClick = { activeTab = SidebarTab.Friends },
                        text = { Text("Friends (${friendStatuses.size})") }
                    )
                    Tab(
                        selected = activeTab == SidebarTab.Groups,
                        onClick = { activeTab = SidebarTab.Groups },
                        text = { Text("Groups (${groups.size})") }
                    )
                }

                IconButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // List of Friends or Groups
            Box(modifier = Modifier.weight(1f)) {
                if (activeTab == SidebarTab.Friends) {
                    if (friendStatuses.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "No friends yet.\nTap '+' to add by code!",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(friendStatuses) { status ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(status.friend.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(status.friend.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(status.friend.friendCode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                                        Text("${status.friend.currentStreak}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (groups.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                "No groups yet.\nTap '+' to create one!",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(groups) { group ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text("${group.memberUids.size} members", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                                        Text("${group.streak}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Footer: Privacy & Settings
            Column {
                Text("Privacy & Settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                // Screen time share toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Share Screen Time", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text("Allow friends to view your screen time with your summary", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isScreenTimeShared,
                        onCheckedChange = {
                            isScreenTimeShared = it
                            FriendManager.setScreenTimeShared(context, it)
                        }
                    )
                }

                if (!hasUsagePermission) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = { ScreenTimeHelper.openUsageSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Grant Usage Access Permission")
                    }
                }
            }
        }
    }
}
