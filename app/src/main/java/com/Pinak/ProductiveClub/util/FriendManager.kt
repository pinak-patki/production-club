package com.Pinak.ProductiveClub.util

import android.content.Context
import android.content.SharedPreferences
import com.Pinak.ProductiveClub.data.Summary
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.random.Random

data class FriendUser(
    val uid: String = "",
    val friendCode: String = "",
    val displayName: String = "Friend",
    val avatarEmoji: String = "🚀",
    val currentStreak: Int = 1,
    val todayScreenTime: Int = 0
)

data class FriendStatus(
    val friend: FriendUser,
    val summary: Summary? = null,
    val duoStreak: Int = 0
)

data class GroupInfo(
    val id: String = "",
    val name: String = "",
    val groupCode: String = "",
    val creatorUid: String = "",
    val memberUids: List<String> = emptyList(),
    val streak: Int = 1,
    val lastStreakDate: String? = null
)

data class GroupMemberProgress(
    val user: FriendUser,
    val isOwner: Boolean,
    val todaySummary: Summary? = null
)

object FriendManager {
    private val db = FirebaseFirestore.getInstance()
    private const val PREFS_NAME = "productive_club_prefs"
    private const val KEY_FRIEND_CODE = "my_friend_code"
    private const val KEY_SHARE_SCREEN_TIME = "share_screen_time"
    private const val KEY_DISPLAY_NAME = "my_display_name"
    private const val KEY_AVATAR_EMOJI = "my_avatar_emoji"
    private const val KEY_ONBOARDED = "is_onboarded"
    private const val KEY_TERMS_ACCEPTED = "is_terms_accepted_v1"

    private val _friendStatuses = MutableStateFlow<List<FriendStatus>>(emptyList())
    val friendStatuses: StateFlow<List<FriendStatus>> = _friendStatuses

    private val _groups = MutableStateFlow<List<GroupInfo>>(emptyList())
    val groups: StateFlow<List<GroupInfo>> = _groups

    private val _myFriendCode = MutableStateFlow<String>("")
    val myFriendCode: StateFlow<String> = _myFriendCode

    private val _myDisplayName = MutableStateFlow<String>("Player")
    val myDisplayName: StateFlow<String> = _myDisplayName

    private val _myAvatarEmoji = MutableStateFlow<String>("🚀")
    val myAvatarEmoji: StateFlow<String> = _myAvatarEmoji

    private val _myStreak = MutableStateFlow<Int>(1)
    val myStreak: StateFlow<Int> = _myStreak

    private val _isInitialized = MutableStateFlow<Boolean>(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError

    fun clearError() {
        _initError.value = null
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboarded(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ONBOARDED, false)
    }

    fun isSecurityOnboarded(context: Context): Boolean {
        val prefs = getPrefs(context)
        return prefs.getBoolean(KEY_ONBOARDED, false) && prefs.getBoolean(KEY_TERMS_ACCEPTED, false)
    }

    fun setTermsAccepted(context: Context, accepted: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TERMS_ACCEPTED, accepted).apply()
    }

    suspend fun updateMyProfile(context: Context, name: String, avatar: String) = withContext(Dispatchers.IO) {
        val uid = AuthManager.currentUserId ?: return@withContext
        val trimmedName = name.trim()
        val prefs = getPrefs(context)
        prefs.edit()
            .putString(KEY_DISPLAY_NAME, trimmedName)
            .putString(KEY_AVATAR_EMOJI, avatar)
            .putBoolean(KEY_ONBOARDED, true)
            .putBoolean(KEY_TERMS_ACCEPTED, true)
            .apply()

        _myDisplayName.value = trimmedName
        _myAvatarEmoji.value = avatar

        try {
            db.collection("users").document(uid).set(
                mapOf(
                    "displayName" to trimmedName,
                    "avatarEmoji" to avatar
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isScreenTimeShared(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHARE_SCREEN_TIME, true)
    }

    fun setScreenTimeShared(context: Context, shared: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHARE_SCREEN_TIME, shared).apply()
    }

    suspend fun syncMyScreenTime(context: Context, minutes: Int) = withContext(Dispatchers.IO) {
        val uid = AuthManager.currentUserId ?: return@withContext
        if (!isScreenTimeShared(context)) return@withContext
        try {
            db.collection("users").document(uid).set(
                mapOf(
                    "todayScreenTime" to minutes,
                    "lastScreenTimeDate" to LocalDate.now().toString()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()
        } catch (_: Exception) {}
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        val uid = AuthManager.currentUserId ?: run {
            _initError.value = "Authentication required"
            _isInitialized.value = true
            return@withContext
        }
        val prefs = getPrefs(context)
        var code = prefs.getString(KEY_FRIEND_CODE, null)
        var name = prefs.getString(KEY_DISPLAY_NAME, null)
        var avatar = prefs.getString(KEY_AVATAR_EMOJI, null)
        var streak = 1

        try {
            // Check Firestore first if missing
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (code == null) code = userDoc.getString("friendCode")
                if (name == null) name = userDoc.getString("displayName")
                if (avatar == null) avatar = userDoc.getString("avatarEmoji")
                val storedStreak = (userDoc.getLong("currentStreak") ?: 0).toInt()
                val lastProofDate = userDoc.getString("lastProofDate")
                val yesterdayStr = LocalDate.now().minusDays(1).toString()

                // If user has a previous proof date that is strictly older than yesterday, streak breaks!
                if (lastProofDate != null && lastProofDate < yesterdayStr) {
                    streak = 0
                    db.collection("users").document(uid).set(
                        mapOf("currentStreak" to 0),
                        SetOptions.merge()
                    )
                } else {
                    streak = storedStreak
                }
            } catch (_: Exception) {}

            if (code == null) {
                // Generate unique 6-character code
                code = "PC-" + Random.nextInt(1000, 9999)
                prefs.edit().putString(KEY_FRIEND_CODE, code).apply()
                if (name == null) name = "Player ${code.takeLast(4)}"
                if (avatar == null) avatar = "🚀"
                try {
                    db.collection("users").document(uid).set(
                        mapOf(
                            "friendCode" to code,
                            "displayName" to name,
                            "avatarEmoji" to avatar,
                            "currentStreak" to 1
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                    db.collection("friendCodes").document(code).set(
                        mapOf("uid" to uid)
                    ).await()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            _myFriendCode.value = code
            _myDisplayName.value = name ?: "Player"
            _myAvatarEmoji.value = avatar ?: "🚀"
            _myStreak.value = streak

            refreshFriendsAndGroups()
        } catch (e: Exception) {
            e.printStackTrace()
            _initError.value = e.localizedMessage ?: "Failed to initialize"
        } finally {
            _isInitialized.value = true
        }
    }

    suspend fun refreshFriendsAndGroups() = withContext(Dispatchers.IO) {
        val uid = AuthManager.currentUserId ?: return@withContext
        val today = LocalDate.now().toString()
        val yesterday = LocalDate.now().minusDays(1).toString()

        try {
            val userDoc = db.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            val friendUids = userDoc.get("friends") as? List<String> ?: emptyList()

            val statuses = mutableListOf<FriendStatus>()
            for (fUid in friendUids) {
                try {
                    val fDoc = db.collection("users").document(fUid).get().await()
                    val fCode = fDoc.getString("friendCode") ?: "PC-0000"
                    val fName = fDoc.getString("displayName") ?: "Friend"
                    val fAvatar = fDoc.getString("avatarEmoji") ?: "🚀"
                    val fStreak = (fDoc.getLong("currentStreak") ?: 0).toInt()
                    val fScreenTime = if (fDoc.getString("lastScreenTimeDate") == today) {
                        (fDoc.getLong("todayScreenTime") ?: 0).toInt()
                    } else 0

                    // Check if friend uploaded summary today
                    val sumDoc = db.collection("users")
                        .document(fUid)
                        .collection("summaries")
                        .document(today)
                        .get()
                        .await()
                    val summary = if (sumDoc.exists()) sumDoc.toObject(Summary::class.java) else null

                    // Fetch mutual duo streak
                    val pairId = getFriendshipPairId(uid, fUid)
                    val pairDoc = db.collection("friendships").document(pairId).get().await()
                    var duoStreak = (pairDoc.getLong("duoStreak") ?: 0).toInt()
                    val lastDuoDate = pairDoc.getString("lastStreakDate")
                    if (lastDuoDate != null && lastDuoDate < yesterday) {
                        duoStreak = 0
                    }

                    statuses.add(
                        FriendStatus(
                            friend = FriendUser(
                                uid = fUid,
                                friendCode = fCode,
                                displayName = fName,
                                avatarEmoji = fAvatar,
                                currentStreak = fStreak,
                                todayScreenTime = fScreenTime
                            ),
                            summary = summary,
                            duoStreak = duoStreak
                        )
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _friendStatuses.value = statuses

            // Groups
            @Suppress("UNCHECKED_CAST")
            val groupIds = userDoc.get("groups") as? List<String> ?: emptyList()
            val groupsList = mutableListOf<GroupInfo>()
            for (gId in groupIds) {
                try {
                    val gDoc = db.collection("groups").document(gId).get().await()
                    if (gDoc.exists()) {
                        @Suppress("UNCHECKED_CAST")
                        val members = gDoc.get("members") as? List<String> ?: emptyList()
                        val gName = gDoc.getString("name") ?: "Study Group"
                        var gCode = gDoc.getString("groupCode") ?: ""
                        val cUid = gDoc.getString("creatorUid") ?: ""
                        val gStreak = (gDoc.getLong("streak") ?: 0).toInt()
                        val lastStreakDate = gDoc.getString("lastStreakDate")

                        // If existing group didn't have groupCode, create and save one retroactively
                        if (gCode.isBlank()) {
                            gCode = "GRP-" + Random.nextInt(1000, 9999)
                            try {
                                db.collection("groups").document(gId).update("groupCode", gCode)
                                db.collection("groupCodes").document(gCode).set(mapOf("groupId" to gId))
                            } catch (_: Exception) {}
                        }

                        groupsList.add(
                            GroupInfo(
                                id = gId,
                                name = gName,
                                groupCode = gCode,
                                creatorUid = cUid,
                                memberUids = members,
                                streak = gStreak,
                                lastStreakDate = lastStreakDate
                            )
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            _groups.value = groupsList
            _initError.value = null
        } catch (e: Exception) {
            e.printStackTrace()
            _initError.value = e.localizedMessage ?: "Failed to load community data"
        }
    }

    suspend fun addFriendByCode(codeToFind: String): Result<String> = withContext(Dispatchers.IO) {
        val currentUid = AuthManager.currentUserId ?: return@withContext Result.failure(Exception("Not signed in"))
        val trimmedCode = codeToFind.trim().uppercase()

        if (trimmedCode == _myFriendCode.value) {
            return@withContext Result.failure(Exception("You cannot add your own code"))
        }

        try {
            val codeDoc = db.collection("friendCodes").document(trimmedCode).get().await()
            if (!codeDoc.exists()) {
                return@withContext Result.failure(Exception("Friend code not found. Check the code and try again."))
            }

            val friendUid = codeDoc.getString("uid") ?: return@withContext Result.failure(Exception("Invalid friend account"))

            // Add mutual friendship
            db.collection("users").document(currentUid)
                .set(mapOf("friends" to FieldValue.arrayUnion(friendUid)), SetOptions.merge())
                .await()

            db.collection("users").document(friendUid)
                .set(mapOf("friends" to FieldValue.arrayUnion(currentUid)), SetOptions.merge())
                .await()

            refreshFriendsAndGroups()
            Result.success("Friend added successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createGroup(groupName: String, selectedFriendUids: List<String>): Result<String> = withContext(Dispatchers.IO) {
        val currentUid = AuthManager.currentUserId ?: return@withContext Result.failure(Exception("Not signed in"))
        if (groupName.isBlank()) {
            return@withContext Result.failure(Exception("Group name cannot be empty"))
        }

        val allMembers = (selectedFriendUids + currentUid).distinct()
        try {
            // Generate unique group code with collision prevention
            var groupCode = "GRP-" + Random.nextInt(1000, 9999)
            var attempts = 0
            while (attempts < 5) {
                val existing = db.collection("groupCodes").document(groupCode).get().await()
                if (!existing.exists()) break
                groupCode = "GRP-" + Random.nextInt(1000, 9999)
                attempts++
            }

            val groupRef = db.collection("groups").document()
            val groupData = mapOf(
                "name" to groupName.trim(),
                "groupCode" to groupCode,
                "creatorUid" to currentUid,
                "members" to allMembers,
                "streak" to 1,
                "createdAt" to FieldValue.serverTimestamp()
            )
            groupRef.set(groupData).await()

            // Save lookup mapping in groupCodes collection
            db.collection("groupCodes").document(groupCode).set(
                mapOf("groupId" to groupRef.id)
            ).await()

            // Add group to all members
            for (mUid in allMembers) {
                try {
                    db.collection("users").document(mUid)
                        .set(mapOf("groups" to FieldValue.arrayUnion(groupRef.id)), SetOptions.merge())
                        .await()
                } catch (_: Exception) {}
            }

            refreshFriendsAndGroups()
            Result.success("Group created! Code: $groupCode")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinGroupByCode(codeToFind: String): Result<String> = withContext(Dispatchers.IO) {
        val currentUid = AuthManager.currentUserId ?: return@withContext Result.failure(Exception("Not signed in"))
        val trimmedCode = codeToFind.trim().uppercase()

        if (trimmedCode.isBlank()) {
            return@withContext Result.failure(Exception("Please enter a group code"))
        }

        try {
            val codeDoc = db.collection("groupCodes").document(trimmedCode).get().await()
            if (!codeDoc.exists()) {
                return@withContext Result.failure(Exception("Group code not found. Check the code and try again."))
            }

            val groupId = codeDoc.getString("groupId") ?: return@withContext Result.failure(Exception("Invalid group record"))
            val groupDoc = db.collection("groups").document(groupId).get().await()
            if (!groupDoc.exists()) {
                return@withContext Result.failure(Exception("This group no longer exists."))
            }

            @Suppress("UNCHECKED_CAST")
            val members = groupDoc.get("members") as? List<String> ?: emptyList()
            if (currentUid in members) {
                return@withContext Result.failure(Exception("You are already a member of this group!"))
            }

            val groupName = groupDoc.getString("name") ?: "Group"

            // Add member to group
            db.collection("groups").document(groupId)
                .update("members", FieldValue.arrayUnion(currentUid))
                .await()

            // Add group to user
            db.collection("users").document(currentUid)
                .set(mapOf("groups" to FieldValue.arrayUnion(groupId)), SetOptions.merge())
                .await()

            refreshFriendsAndGroups()
            Result.success("Joined \"$groupName\" successfully!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addMembersToGroup(groupId: String, newFriendUids: List<String>): Result<String> = withContext(Dispatchers.IO) {
        if (newFriendUids.isEmpty()) return@withContext Result.success("No friends selected")
        try {
            db.collection("groups").document(groupId)
                .update("members", FieldValue.arrayUnion(*newFriendUids.toTypedArray()))
                .await()

            for (fUid in newFriendUids) {
                try {
                    db.collection("users").document(fUid)
                        .set(mapOf("groups" to FieldValue.arrayUnion(groupId)), SetOptions.merge())
                        .await()
                } catch (_: Exception) {}
            }

            refreshFriendsAndGroups()
            Result.success("Friends added to group!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveGroup(groupId: String): Result<String> = withContext(Dispatchers.IO) {
        val currentUid = AuthManager.currentUserId ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            db.collection("groups").document(groupId)
                .update("members", FieldValue.arrayRemove(currentUid))
                .await()

            db.collection("users").document(currentUid)
                .update("groups", FieldValue.arrayRemove(groupId))
                .await()

            refreshFriendsAndGroups()
            Result.success("Left the group")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchGroupMemberDetails(memberUids: List<String>): List<FriendUser> = withContext(Dispatchers.IO) {
        val result = mutableListOf<FriendUser>()
        for (uid in memberUids) {
            try {
                val doc = db.collection("users").document(uid).get().await()
                if (doc.exists()) {
                    result.add(
                        FriendUser(
                            uid = uid,
                            friendCode = doc.getString("friendCode") ?: "",
                            displayName = doc.getString("displayName") ?: "Member",
                            avatarEmoji = doc.getString("avatarEmoji") ?: "👤",
                            currentStreak = (doc.getLong("currentStreak") ?: 0).toInt(),
                            todayScreenTime = (doc.getLong("todayScreenTime") ?: 0).toInt()
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        result
    }

    suspend fun recordDailyProofSubmission(uid: String) = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStr = today.toString()
        val yesterdayStr = yesterday.toString()

        try {
            val userDoc = db.collection("users").document(uid).get().await()
            val lastProofDate = userDoc.getString("lastProofDate")
            val current = (userDoc.getLong("currentStreak") ?: 0).toInt()

            val newStreak = when (lastProofDate) {
                todayStr -> current // Already recorded today
                yesterdayStr -> current + 1 // Maintained & advanced!
                else -> 1 // Started fresh or after broken streak
            }

            db.collection("users").document(uid).set(
                mapOf(
                    "currentStreak" to newStreak,
                    "lastProofDate" to todayStr
                ),
                SetOptions.merge()
            ).await()

            _myStreak.value = newStreak

            // Evaluate all groups this user belongs to for 75% streak rule
            @Suppress("UNCHECKED_CAST")
            val groupIds = userDoc.get("groups") as? List<String> ?: emptyList()
            for (gId in groupIds) {
                checkAndUpdateGroupStreak(gId)
            }

            // Evaluate Mutual Duo Streaks with all friends
            @Suppress("UNCHECKED_CAST")
            val friendUids = userDoc.get("friends") as? List<String> ?: emptyList()
            for (fUid in friendUids) {
                try {
                    val fSummaryDoc = db.collection("users").document(fUid)
                        .collection("summaries").document(todayStr).get().await()
                    if (fSummaryDoc.exists()) {
                        // Friend also submitted today! Advance or maintain duo streak
                        val pairId = getFriendshipPairId(uid, fUid)
                        val pairDoc = db.collection("friendships").document(pairId).get().await()
                        val currentDuo = (pairDoc.getLong("duoStreak") ?: 0).toInt()
                        val lastDuoDate = pairDoc.getString("lastStreakDate")

                        val newDuo = when (lastDuoDate) {
                            todayStr -> currentDuo
                            yesterdayStr -> currentDuo + 1
                            else -> 1
                        }

                        db.collection("friendships").document(pairId).set(
                            mapOf(
                                "duoStreak" to newDuo,
                                "lastStreakDate" to todayStr,
                                "userA" to uid,
                                "userB" to fUid
                            ),
                            SetOptions.merge()
                        ).await()
                    }
                } catch (_: Exception) {}
            }

            refreshFriendsAndGroups()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun checkAndUpdateGroupStreak(groupId: String): GroupInfo? = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val todayStr = today.toString()
        val yesterdayStr = yesterday.toString()

        try {
            val gDoc = db.collection("groups").document(groupId).get().await()
            if (!gDoc.exists()) return@withContext null

            @Suppress("UNCHECKED_CAST")
            val members = gDoc.get("members") as? List<String> ?: emptyList()
            if (members.isEmpty()) return@withContext null

            val currentGroupStreak = (gDoc.getLong("streak") ?: 0).toInt()
            val lastStreakDate = gDoc.getString("lastStreakDate")

            // Count how many members uploaded today
            var todayUploads = 0
            for (mUid in members) {
                try {
                    val sumDoc = db.collection("users").document(mUid)
                        .collection("summaries").document(todayStr).get().await()
                    if (sumDoc.exists()) todayUploads++
                } catch (_: Exception) {}
            }

            val ratio = todayUploads.toDouble() / members.size.toDouble()
            var newGroupStreak = currentGroupStreak

            if (ratio >= 0.75) {
                // Criteria met for today (at least 75%)!
                if (lastStreakDate == yesterdayStr) {
                    newGroupStreak = currentGroupStreak + 1
                    db.collection("groups").document(groupId).set(
                        mapOf("streak" to newGroupStreak, "lastStreakDate" to todayStr),
                        SetOptions.merge()
                    ).await()
                } else if (lastStreakDate != todayStr) {
                    newGroupStreak = 1
                    db.collection("groups").document(groupId).set(
                        mapOf("streak" to 1, "lastStreakDate" to todayStr),
                        SetOptions.merge()
                    ).await()
                }
            } else {
                // If yesterday was missed without reaching 75%
                if (lastStreakDate != null && lastStreakDate < yesterdayStr) {
                    newGroupStreak = 0
                    db.collection("groups").document(groupId).set(
                        mapOf("streak" to 0),
                        SetOptions.merge()
                    ).await()
                }
            }

            GroupInfo(
                id = groupId,
                name = gDoc.getString("name") ?: "Group",
                groupCode = gDoc.getString("groupCode") ?: "",
                creatorUid = gDoc.getString("creatorUid") ?: "",
                memberUids = members,
                streak = newGroupStreak,
                lastStreakDate = if (ratio >= 0.75) todayStr else lastStreakDate
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchGroupMembersWithProgress(
        groupId: String,
        memberUids: List<String>,
        creatorUid: String
    ): List<GroupMemberProgress> = withContext(Dispatchers.IO) {
        val todayStr = LocalDate.now().toString()
        val result = mutableListOf<GroupMemberProgress>()

        for (uid in memberUids) {
            try {
                val userDoc = db.collection("users").document(uid).get().await()
                if (userDoc.exists()) {
                    val fCode = userDoc.getString("friendCode") ?: ""
                    val fName = userDoc.getString("displayName") ?: "Member"
                    val fAvatar = userDoc.getString("avatarEmoji") ?: "👤"
                    val fStreak = (userDoc.getLong("currentStreak") ?: 0).toInt()
                    val fScreenTime = if (userDoc.getString("lastScreenTimeDate") == todayStr) {
                        (userDoc.getLong("todayScreenTime") ?: 0).toInt()
                    } else 0

                    val sumDoc = db.collection("users")
                        .document(uid)
                        .collection("summaries")
                        .document(todayStr)
                        .get()
                        .await()
                    val summary = if (sumDoc.exists()) sumDoc.toObject(Summary::class.java) else null

                    result.add(
                        GroupMemberProgress(
                            user = FriendUser(
                                uid = uid,
                                friendCode = fCode,
                                displayName = fName,
                                avatarEmoji = fAvatar,
                                currentStreak = fStreak,
                                todayScreenTime = fScreenTime
                            ),
                            isOwner = (uid == creatorUid),
                            todaySummary = summary
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        result
    }

    // --- GROUP OWNER GOVERNANCE METHODS ---

    suspend fun kickGroupMember(groupId: String, memberUid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            db.collection("groups").document(groupId)
                .update("members", FieldValue.arrayRemove(memberUid))
                .await()

            db.collection("users").document(memberUid)
                .set(mapOf("groups" to FieldValue.arrayRemove(groupId)), SetOptions.merge())
                .await()

            refreshFriendsAndGroups()
            Result.success("Member removed from group")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameGroup(groupId: String, newName: String): Result<String> = withContext(Dispatchers.IO) {
        if (newName.isBlank()) return@withContext Result.failure(Exception("Group name cannot be blank"))
        try {
            db.collection("groups").document(groupId)
                .set(mapOf("name" to newName.trim()), SetOptions.merge())
                .await()

            refreshFriendsAndGroups()
            Result.success("Group renamed to \"${newName.trim()}\"")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun regenerateGroupCode(groupId: String, oldCode: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Invalidate old code
            if (oldCode.isNotBlank()) {
                db.collection("groupCodes").document(oldCode).delete().await()
            }

            // Generate new code with clash detection
            var newCode = "GRP-" + Random.nextInt(1000, 9999)
            var attempts = 0
            while (attempts < 5) {
                val existing = db.collection("groupCodes").document(newCode).get().await()
                if (!existing.exists()) break
                newCode = "GRP-" + Random.nextInt(1000, 9999)
                attempts++
            }

            db.collection("groups").document(groupId).set(
                mapOf("groupCode" to newCode),
                SetOptions.merge()
            ).await()

            db.collection("groupCodes").document(newCode).set(
                mapOf("groupId" to groupId)
            ).await()

            refreshFriendsAndGroups()
            Result.success(newCode)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun transferGroupOwnership(groupId: String, newOwnerUid: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            db.collection("groups").document(groupId).set(
                mapOf("creatorUid" to newOwnerUid),
                SetOptions.merge()
            ).await()

            refreshFriendsAndGroups()
            Result.success("Ownership transferred successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteGroup(groupId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val gDoc = db.collection("groups").document(groupId).get().await()
            if (gDoc.exists()) {
                val code = gDoc.getString("groupCode") ?: ""
                @Suppress("UNCHECKED_CAST")
                val members = gDoc.get("members") as? List<String> ?: emptyList()

                if (code.isNotBlank()) {
                    db.collection("groupCodes").document(code).delete().await()
                }

                // Remove group from all members
                for (mUid in members) {
                    try {
                        db.collection("users").document(mUid)
                            .set(mapOf("groups" to FieldValue.arrayRemove(groupId)), SetOptions.merge())
                            .await()
                    } catch (_: Exception) {}
                }

                db.collection("groups").document(groupId).delete().await()
            }

            refreshFriendsAndGroups()
            Result.success("Group deleted successfully")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getFriendshipPairId(uidA: String, uidB: String): String {
        return if (uidA < uidB) "${uidA}_${uidB}" else "${uidB}_${uidA}"
    }

    suspend fun removeFriend(friendUid: String): Result<String> = withContext(Dispatchers.IO) {
        val myUid = AuthManager.currentUserId ?: return@withContext Result.failure(Exception("Not signed in"))
        try {
            // Remove friendUid from current user's friends list
            db.collection("users").document(myUid)
                .update("friends", FieldValue.arrayRemove(friendUid))
                .await()

            // Remove current user from friend's friends list
            db.collection("users").document(friendUid)
                .update("friends", FieldValue.arrayRemove(myUid))
                .await()

            // Delete mutual friendship document
            val pairId = getFriendshipPairId(myUid, friendUid)
            try {
                db.collection("friendships").document(pairId).delete().await()
            } catch (_: Exception) {}

            refreshFriendsAndGroups()
            Result.success("Friend removed")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
