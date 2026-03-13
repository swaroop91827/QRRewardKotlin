package com.swaroopsingh.qrreward.manager

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object FirebaseManager {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().reference

    // All remotely controllable via Firebase
    var maxDailyRewardScans: Int = 15       // Start 15, can change to 20 via Firebase
    var cooldownSeconds: Int = 30            // 30s between reward scans
    var scratchCardActive: Boolean = true
    var winProb10: Int = 70
    var winProb50: Int = 25
    var winProb100: Int = 5
    var heroAffiliateLink: String = ""
    var heroBannerImage: String = ""

    fun initUser(onReady: (uid: String) -> Unit) {
        if (auth.currentUser != null) { onReady(auth.currentUser!!.uid); return }
        auth.signInAnonymously().addOnSuccessListener { onReady(it.user!!.uid) }
    }

    fun fetchAndCacheSettings(onDone: () -> Unit) {
        db.child("app_settings").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                maxDailyRewardScans = snap.child("max_daily_scans").getValue(Int::class.java) ?: 15
                cooldownSeconds = snap.child("cooldown_seconds").getValue(Int::class.java) ?: 30
                scratchCardActive = snap.child("scratch_card_active").getValue(Boolean::class.java) ?: true
                winProb10 = snap.child("win_probability/10_pts").getValue(Int::class.java) ?: 70
                winProb50 = snap.child("win_probability/50_pts").getValue(Int::class.java) ?: 25
                winProb100 = snap.child("win_probability/100_pts").getValue(Int::class.java) ?: 5
                heroAffiliateLink = snap.child("hero_affiliate_link").getValue(String::class.java) ?: ""
                heroBannerImage = snap.child("hero_banner_image").getValue(String::class.java) ?: ""
                onDone()
            }
            override fun onCancelled(error: DatabaseError) { onDone() }
        })
    }

    fun getUserData(uid: String, onData: (wallet: Int, rewardScansToday: Int, lastTs: Long) -> Unit) {
        db.child("users").child(uid).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snap: DataSnapshot) {
                val wallet = snap.child("wallet_balance").getValue(Int::class.java) ?: 0
                val rewardScans = snap.child("reward_scans_today").getValue(Int::class.java) ?: 0
                val ts = snap.child("last_reward_timestamp").getValue(Long::class.java) ?: 0L
                onData(wallet, rewardScans, ts)
            }
            override fun onCancelled(error: DatabaseError) { onData(0, 0, 0L) }
        })
    }

    // 3 states: CAN_EARN, COOLDOWN, LIMIT_REACHED
    enum class RewardState { CAN_EARN, COOLDOWN, LIMIT_REACHED }

    fun getRewardState(rewardScansToday: Int, lastRewardTimestamp: Long): RewardState {
        // State 1: Daily limit reached
        if (rewardScansToday >= maxDailyRewardScans) return RewardState.LIMIT_REACHED

        // State 2: Cooldown active (30-45 seconds between rewards)
        val secondsSinceLast = (System.currentTimeMillis() - lastRewardTimestamp) / 1000
        if (lastRewardTimestamp > 0 && secondsSinceLast < cooldownSeconds) return RewardState.COOLDOWN

        // State 3: Can earn reward
        return RewardState.CAN_EARN
    }

    fun getCooldownSecondsLeft(lastRewardTimestamp: Long): Int {
        val secondsSinceLast = (System.currentTimeMillis() - lastRewardTimestamp) / 1000
        return (cooldownSeconds - secondsSinceLast).toInt().coerceAtLeast(0)
    }

    @Volatile private var isUpdating = false

    fun addPoints(uid: String, points: Int, onSuccess: (newBalance: Int) -> Unit) {
        if (isUpdating) return
        isUpdating = true
        db.child("users").child(uid).runTransaction(object : Transaction.Handler {
            override fun doTransaction(data: MutableData): Transaction.Result {
                val wallet = data.child("wallet_balance").getValue(Int::class.java) ?: 0
                val rewardScans = data.child("reward_scans_today").getValue(Int::class.java) ?: 0
                data.child("wallet_balance").value = wallet + points
                data.child("reward_scans_today").value = rewardScans + 1
                data.child("last_reward_timestamp").value = System.currentTimeMillis()
                return Transaction.success(data)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snap: DataSnapshot?) {
                isUpdating = false
                if (committed && snap != null)
                    onSuccess(snap.child("wallet_balance").getValue(Int::class.java) ?: 0)
            }
        })
    }

    fun checkAndResetDailyRewards(uid: String, lastTimestamp: Long) {
        val last = java.util.Calendar.getInstance().apply { timeInMillis = lastTimestamp }
        val today = java.util.Calendar.getInstance()
        if (last.get(java.util.Calendar.DAY_OF_YEAR) != today.get(java.util.Calendar.DAY_OF_YEAR))
            db.child("users").child(uid).child("reward_scans_today").setValue(0)
    }
}
