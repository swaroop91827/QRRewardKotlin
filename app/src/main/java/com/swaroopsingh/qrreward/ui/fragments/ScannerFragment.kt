package com.swaroopsingh.qrreward.ui.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CountDownTimer
import android.view.*
import android.widget.*
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.swaroopsingh.qrreward.R
import com.swaroopsingh.qrreward.engine.ProbabilityEngine
import com.swaroopsingh.qrreward.manager.FirebaseManager
import com.swaroopsingh.qrreward.manager.FirebaseManager.RewardState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private lateinit var uid: String
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var tvPoints: TextView
    private lateinit var tvScanCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tvCooldown: TextView

    private var rewardScansToday = 0
    private var lastRewardTimestamp = 0L
    private var walletBalance = 0
    private var isProcessing = false
    private var cooldownTimer: CountDownTimer? = null

    companion object {
        fun newInstance(uid: String) = ScannerFragment().apply {
            arguments = Bundle().apply { putString("uid", uid) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = arguments?.getString("uid") ?: ""
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_scanner, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        previewView = view.findViewById(R.id.preview_view)
        tvPoints = view.findViewById(R.id.tv_points)
        tvScanCount = view.findViewById(R.id.tv_scan_count)
        progressBar = view.findViewById(R.id.progress_scans)
        tvStatus = view.findViewById(R.id.tv_status)
        tvCooldown = view.findViewById(R.id.tv_cooldown)

        loadUserData()
        checkPermissionAndStartCamera()
    }

    private fun loadUserData() {
        FirebaseManager.getUserData(uid) { wallet, rewardScans, lastTs ->
            walletBalance = wallet
            rewardScansToday = rewardScans
            lastRewardTimestamp = lastTs
            activity?.runOnUiThread { updateHUD() }
        }
    }

    private fun updateHUD() {
        val max = FirebaseManager.maxDailyRewardScans
        // 🪙 Points
        tvPoints.text = "🪙 $walletBalance"
        // ⚡ Energy bar
        tvScanCount.text = "⚡ $rewardScansToday/$max Rewards"
        progressBar.max = max
        progressBar.progress = rewardScansToday
        // Golden when full
        if (rewardScansToday >= max) {
            tvStatus.text = "🏆 Daily Target Reached! Come back tomorrow."
            tvStatus.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionAndStartCamera() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 100)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also {
                    it.setAnalyzer(cameraExecutor, ::analyzeImage)
                }
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                preview, imageAnalyzer
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (isProcessing) { imageProxy.close(); return }
        val mediaImage = imageProxy.image ?: run { imageProxy.close(); return }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        BarcodeScanning.getClient().process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty() && !isProcessing) {
                    val result = barcodes[0].rawValue ?: ""
                    isProcessing = true
                    activity?.runOnUiThread { handleScanResult(result) }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun handleScanResult(data: String) {
        val state = FirebaseManager.getRewardState(rewardScansToday, lastRewardTimestamp)
        when (state) {
            RewardState.LIMIT_REACHED -> {
                // Scan still works, just no reward
                showScanResultOnly(data, "Daily reward limit reached. Scanner still active!")
                isProcessing = false
            }
            RewardState.COOLDOWN -> {
                val secsLeft = FirebaseManager.getCooldownSecondsLeft(lastRewardTimestamp)
                showScanResultOnly(data, "Wait ${secsLeft}s for next reward")
                startCooldownTimer(secsLeft)
                isProcessing = false
            }
            RewardState.CAN_EARN -> {
                // Full reward flow: Ad → Scratch Card
                showProcessingAndReward(data)
            }
        }
    }

    private fun showScanResultOnly(data: String, msg: String) {
        tvStatus.text = "✅ Scanned: ${data.take(40)}...\n$msg"
        tvStatus.visibility = View.VISIBLE
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            isProcessing = false
            tvStatus.visibility = View.GONE
        }, 2000)
    }

    private fun showProcessingAndReward(data: String) {
        tvStatus.text = "⚡ Processing..."
        tvStatus.visibility = View.VISIBLE
        // TODO: Load AdMob Interstitial here
        // After ad closes, show scratch card
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            showScratchCard(data)
        }, 1500)
    }

    private fun showScratchCard(data: String) {
        val result = ProbabilityEngine.calculateReward()
        val dialog = ScratchCardDialog.newInstance(result.points, result.message, result.emoji, data, uid)
        dialog.onPointsAwarded = { newBalance ->
            walletBalance = newBalance
            rewardScansToday++
            lastRewardTimestamp = System.currentTimeMillis()
            updateHUD()
            isProcessing = false
        }
        dialog.show(parentFragmentManager, "scratch_card")
    }

    private fun startCooldownTimer(seconds: Int) {
        cooldownTimer?.cancel()
        tvCooldown.visibility = View.VISIBLE
        cooldownTimer = object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(ms: Long) {
                tvCooldown.text = "Next reward in ${ms / 1000}s"
            }
            override fun onFinish() {
                tvCooldown.visibility = View.GONE
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        cooldownTimer?.cancel()
    }
}
