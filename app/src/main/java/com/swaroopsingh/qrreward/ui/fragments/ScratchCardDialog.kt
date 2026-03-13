package com.swaroopsingh.qrreward.ui.fragments

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.DialogFragment
import com.swaroopsingh.qrreward.R
import com.swaroopsingh.qrreward.manager.FirebaseManager

class ScratchCardDialog : DialogFragment() {

    var onPointsAwarded: ((Int) -> Unit)? = null

    companion object {
        fun newInstance(points: Int, message: String, emoji: String, qrData: String, uid: String) =
            ScratchCardDialog().apply {
                arguments = Bundle().apply {
                    putInt("points", points)
                    putString("message", message)
                    putString("emoji", emoji)
                    putString("qrData", qrData)
                    putString("uid", uid)
                }
            }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.dialog_scratch_card, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val points = arguments?.getInt("points") ?: 0
        val message = arguments?.getString("message") ?: ""
        val emoji = arguments?.getString("emoji") ?: ""
        val qrData = arguments?.getString("qrData") ?: ""
        val uid = arguments?.getString("uid") ?: ""

        val tvEmoji = view.findViewById<TextView>(R.id.tv_scratch_emoji)
        val tvMessage = view.findViewById<TextView>(R.id.tv_scratch_message)
        val tvQrResult = view.findViewById<TextView>(R.id.tv_qr_result)
        val btnClaim = view.findViewById<Button>(R.id.btn_claim)
        val btnShare = view.findViewById<Button>(R.id.btn_share)
        val scratchOverlay = view.findViewById<View>(R.id.scratch_overlay)
        var scratched = false

        tvEmoji.text = emoji
        tvMessage.text = message
        tvQrResult.text = "📋 $qrData"

        // Scratch gesture
        scratchOverlay.setOnTouchListener { v, event ->
            if (!scratched && event.action == MotionEvent.ACTION_MOVE) {
                scratched = true
                scratchOverlay.visibility = View.GONE
                tvEmoji.visibility = View.VISIBLE
                tvMessage.visibility = View.VISIBLE
                btnClaim.visibility = View.VISIBLE
                if (points > 0) btnShare.visibility = View.VISIBLE
            }
            true
        }

        // Claim points
        btnClaim.setOnClickListener {
            if (points > 0) {
                FirebaseManager.addPoints(uid, points) { newBalance ->
                    activity?.runOnUiThread {
                        onPointsAwarded?.invoke(newBalance)
                        dismiss()
                    }
                }
            } else {
                dismiss()
            }
        }

        // Share on WhatsApp = double points
        btnShare.setOnClickListener {
            val shareText = "I just won $points points on QR Reward App! 🎉 Download and earn rewards: https://play.google.com/store/apps/details?id=com.swaroopsingh.qrreward"
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                setPackage("com.whatsapp")
            }
            try {
                startActivity(intent)
                // Double points on share
                FirebaseManager.addPoints(uid, points) { newBalance ->
                    activity?.runOnUiThread {
                        onPointsAwarded?.invoke(newBalance)
                        Toast.makeText(context, "🎉 Points Doubled! +${points * 2} total!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
}
