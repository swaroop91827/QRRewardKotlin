package com.swaroopsingh.qrreward.engine

import com.swaroopsingh.qrreward.manager.FirebaseManager

object ProbabilityEngine {

    data class ScratchResult(
        val points: Int,
        val message: String,
        val emoji: String,
        val isWin: Boolean
    )

    // Local calculation - no server needed (Firebase bill = zero)
    fun calculateReward(): ScratchResult {
        val random = (1..100).random()
        val points = when {
            random <= FirebaseManager.winProb10 -> 10                                          // 70%
            random <= FirebaseManager.winProb10 + FirebaseManager.winProb50 -> 50             // 25%
            random <= 100 -> 100                                                               // 5%
            else -> 0
        }
        return when (points) {
            100 -> ScratchResult(100, "JACKPOT! You Won!", "👑", true)
            50  -> ScratchResult(50,  "Amazing! 50 Points!", "🔥", true)
            10  -> ScratchResult(10,  "You Won 10 Points!", "🎉", true)
            else -> ScratchResult(0,  "Better Luck Next Time!", "😔", false)
        }
    }
}
