package com.Pinak.ProductiveClub.ui.friends

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.Pinak.ProductiveClub.ui.home.FriendProofDialog
import com.Pinak.ProductiveClub.util.FriendManager
import com.Pinak.ProductiveClub.util.FriendStatus
import com.Pinak.ProductiveClub.util.GroupInfo
import com.Pinak.ProductiveClub.util.NetworkHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    isOnline: Boolean,
    onConnectInternet: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val myFriendCode by FriendManager.myFriendCode.collectAsState()
    val friendStatuses by FriendManager.friendStatuses.collectAsState()
    val groups by FriendManager.groups.collectAsState()
    val isInitialized by FriendManager.isInitialized.collectAsState()
    val initError by FriendManager.initError.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Friends, 1: Groups
    var selectedFriendForProof by remember { mutableStateOf<FriendStatus?>(null) }
    var selectedFriendForProfile by remember { mutableStateOf<FriendStatus?>(null) }
    var selectedGroupForDetails by remember { mutableStateOf<GroupInfo?>(null) }

    // Dialog visibility states
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var showJoinGroupDialog by remember { mutableStateOf(false) }

    // Add friend fields
    var friendCodeInput by remember { mutableStateOf("") }
    var addFriendError by remember { mutableStateOf<String?>(null) }
    var isAddingFriend by remember { mutableStateOf(false) }

    // Create group fields
    var groupNameInput by remember { mutableStateOf("") }
    var selectedFriendsForGroup by remember { mutableStateOf(setOf<String>()) }
    var createGroupError by remember { mutableStateOf<String?>(null) }
    var isCreatingGroup by remember { mutableStateOf(false) }

    // Join group fields
    var joinGroupCodeInput by remember { mutableStateOf("") }
    var joinGroupError by remember { mutableStateOf<String?>(null) }
    var isJoiningGroup by remember { mutableStateOf(false) }

    // Friend Profile Sheet / Dialog
    selectedFriendForProfile?.let { status ->
        FriendProfileDialog(
            friendStatus = status,
            onDismiss = { selectedFriendForProfile = null },
            onInspectProof = {
                val s = selectedFriendForProfile
                selectedFriendForProfile = null
                selectedFriendForProof = s
            },
            onFriendRemoved = {
                selectedFriendForProfile = null
                scope.launch { FriendManager.refreshFriendsAndGroups() }
            }
        )
    }

    // Friend Proof Dialog
    selectedFriendForProof?.let { friendStatus ->
        FriendProofDialog(
            friendStatus = friendStatus,
            onDismiss = { selectedFriendForProof = null }
        )
    }

    // Full-Screen Group Hub
    if (selectedGroupForDetails != null) {
        BackHandler {
            selectedGroupForDetails = null
        }
        GroupDetailScreen(
            group = selectedGroupForDetails!!,
            onBack = { selectedGroupForDetails = null },
            onGroupDeleted = {
                selectedGroupForDetails = null
                scope.launch { FriendManager.refreshFriendsAndGroups() }
            }
        )
        return
    }

    // 1. ADD FRIEND DIALOG
    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFriendDialog = false
                addFriendError = null
            },
            title = { Text("Add Friend", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter your friend's unique code:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = friendCodeInput,
                        onValueChange = { friendCodeInput = it.uppercase() },
                        placeholder = { Text("PC-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    addFriendError?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!NetworkHelper.isNetworkAvailable(context)) {
                            addFriendError = "You are offline. Turn on internet to connect."
                            return@Button
                        }
                        scope.launch {
                            isAddingFriend = true
                            addFriendError = null
                            val result = FriendManager.addFriendByCode(friendCodeInput)
                            isAddingFriend = false
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    showAddFriendDialog = false
                                    friendCodeInput = ""
                                },
                                onFailure = { addFriendError = it.message ?: "Failed to add friend" }
                            )
                        }
                    },
                    enabled = !isAddingFriend && friendCodeInput.isNotBlank()
                ) {
                    if (isAddingFriend) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Add")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFriendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 2. CREATE GROUP DIALOG (With optional friend selection)
    if (showCreateGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateGroupDialog = false
                createGroupError = null
            },
            title = { Text("Create Accountability Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Enter a name for your group:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = groupNameInput,
                        onValueChange = { groupNameInput = it },
                        placeholder = { Text("e.g. 5 AM Study Club") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (friendStatuses.isNotEmpty()) {
                        Text(
                            "Invite friends to start with (optional):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(6.dp)) {
                                items(friendStatuses) { status ->
                                    val isChecked = status.friend.uid in selectedFriendsForGroup
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedFriendsForGroup = if (isChecked) {
                                                    selectedFriendsForGroup - status.friend.uid
                                                } else {
                                                    selectedFriendsForGroup + status.friend.uid
                                                }
                                            }
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                selectedFriendsForGroup = if (checked) {
                                                    selectedFriendsForGroup + status.friend.uid
                                                } else {
                                                    selectedFriendsForGroup - status.friend.uid
                                                }
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(status.friend.avatarEmoji, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            status.friend.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    createGroupError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!NetworkHelper.isNetworkAvailable(context)) {
                            createGroupError = "You are offline. Turn on internet to connect."
                            return@Button
                        }
                        scope.launch {
                            isCreatingGroup = true
                            createGroupError = null
                            val result = FriendManager.createGroup(groupNameInput, selectedFriendsForGroup.toList())
                            isCreatingGroup = false
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    showCreateGroupDialog = false
                                    groupNameInput = ""
                                    selectedFriendsForGroup = emptySet()
                                },
                                onFailure = { createGroupError = it.message ?: "Failed to create group" }
                            )
                        }
                    },
                    enabled = !isCreatingGroup && groupNameInput.isNotBlank()
                ) {
                    if (isCreatingGroup) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Create")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 3. JOIN GROUP DIALOG (By GRP-XXXX code)
    if (showJoinGroupDialog) {
        AlertDialog(
            onDismissRequest = {
                showJoinGroupDialog = false
                joinGroupError = null
            },
            title = { Text("Join Group", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter the Group Invite Code shared by your friend:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = joinGroupCodeInput,
                        onValueChange = { joinGroupCodeInput = it.uppercase() },
                        placeholder = { Text("GRP-XXXX") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Ask a group member for their group code to join their accountability streak.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    joinGroupError?.let { err ->
                        Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!NetworkHelper.isNetworkAvailable(context)) {
                            joinGroupError = "You are offline. Turn on internet to connect."
                            return@Button
                        }
                        scope.launch {
                            isJoiningGroup = true
                            joinGroupError = null
                            val result = FriendManager.joinGroupByCode(joinGroupCodeInput)
                            isJoiningGroup = false
                            result.fold(
                                onSuccess = {
                                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                                    showJoinGroupDialog = false
                                    joinGroupCodeInput = ""
                                },
                                onFailure = { joinGroupError = it.message ?: "Failed to join group" }
                            )
                        }
                    },
                    enabled = !isJoiningGroup && joinGroupCodeInput.isNotBlank()
                ) {
                    if (isJoiningGroup) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Join Group")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Community",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = {
                                friendCodeInput = ""
                                addFriendError = null
                                showAddFriendDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = "Add Friend",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                joinGroupCodeInput = ""
                                joinGroupError = null
                                showJoinGroupDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Login,
                                contentDescription = "Join Group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = {
                                groupNameInput = ""
                                selectedFriendsForGroup = emptySet()
                                createGroupError = null
                                showCreateGroupDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Default.GroupAdd,
                                contentDescription = "Create Group",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Friend Code Banner
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Your Code: ", style = MaterialTheme.typography.bodyMedium)
                        Text(myFriendCode.ifEmpty { "..." }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Friend Code", myFriendCode))
                            Toast.makeText(context, "Copied: $myFriendCode", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Copy")
                    }
                }
            }

            // Tab Bar
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Friends (${friendStatuses.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Groups (${groups.size})", fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Groups, contentDescription = null) }
                )
            }

            // Offline notice
            if (!isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Offline — Connect to internet to see friend updates", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onConnectInternet) {
                            Text("Connect")
                        }
                    }
                }
            }

            // Tab Content
            if (selectedTab == 0) {
                // Friends List
                if (!isInitialized && friendStatuses.isEmpty() && isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Loading friends...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (initError != null && friendStatuses.isEmpty() && isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Couldn't load friends",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                initError ?: "An unexpected error occurred",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { scope.launch { FriendManager.refreshFriendsAndGroups() } },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                } else if (friendStatuses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PeopleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No friends yet!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Add your friends using their friend code to track their daily goals, streaks, and proofs.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    friendCodeInput = ""
                                    addFriendError = null
                                    showAddFriendDialog = true
                                }
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Your First Friend")
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(friendStatuses) { status ->
                            val isSubmitted = status.summary != null
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedFriendForProfile = status }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Avatar
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = status.friend.avatarEmoji, fontSize = 24.sp)
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = status.friend.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                status.friend.friendCode,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (status.friend.todayScreenTime > 0) {
                                                Text(
                                                    "• ⏱️ ${status.friend.todayScreenTime}m screen time",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("${status.duoStreak}d Duo", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSubmitted) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                                        ) {
                                            Text(
                                                text = if (isSubmitted) "Submitted ✅" else "Pending ⏳",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isSubmitted) Color(0xFF2E7D32) else Color(0xFFE65100),
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Groups List
                if (!isInitialized && groups.isEmpty() && isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                "Loading groups...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (initError != null && groups.isEmpty() && isOnline) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Couldn't load groups",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                initError ?: "An unexpected error occurred",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { scope.launch { FriendManager.refreshFriendsAndGroups() } },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                } else if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No accountability groups yet!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Create a group to invite friends and build group streaks together.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        groupNameInput = ""
                                        selectedFriendsForGroup = emptySet()
                                        createGroupError = null
                                        showCreateGroupDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }

                                OutlinedButton(
                                    onClick = {
                                        joinGroupCodeInput = ""
                                        joinGroupError = null
                                        showJoinGroupDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Join", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        joinGroupCodeInput = ""
                                        joinGroupError = null
                                        showJoinGroupDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Join Code", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = {
                                        groupNameInput = ""
                                        selectedFriendsForGroup = emptySet()
                                        createGroupError = null
                                        showCreateGroupDialog = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Group", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        items(groups) { group ->
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedGroupForDetails = group }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Groups,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = group.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${group.memberUids.size} members",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (group.groupCode.isNotBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                                ) {
                                                    Text(
                                                        text = group.groupCode,
                                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Whatshot, contentDescription = null, tint = Color(0xFFFF5722), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("${group.streak}d Streak", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
