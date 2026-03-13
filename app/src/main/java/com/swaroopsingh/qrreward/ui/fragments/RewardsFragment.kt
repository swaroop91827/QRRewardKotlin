package com.swaroopsingh.qrreward.ui.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.swaroopsingh.qrreward.R
import com.swaroopsingh.qrreward.manager.FirebaseManager

class RewardsFragment : Fragment() {

    private lateinit var uid: String

    companion object {
        fun newInstance(uid: String) = RewardsFragment().apply {
            arguments = Bundle().apply { putString("uid", uid) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uid = arguments?.getString("uid") ?: ""
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_rewards, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tvBalance = view.findViewById<TextView>(R.id.tv_wallet_balance)
        val btnAmazon = view.findViewById<Button>(R.id.btn_amazon_50)
        val btnFlipcart = view.findViewById<Button>(R.id.btn_flipkart_100)
        val btnMyntra = view.findViewById<Button>(R.id.btn_myntra_50)
        val imgBanner = view.findViewById<ImageView>(R.id.img_hero_banner)
        val tvBannerTitle = view.findViewById<TextView>(R.id.tv_banner_title)

        // Load wallet
        FirebaseManager.getUserData(uid) { wallet, _, _ ->
            activity?.runOnUiThread {
                tvBalance.text = "🪙 $wallet Points"
            }
        }

        // Reward store items
        btnAmazon.setOnClickListener { redeemReward("Amazon ₹50 Voucher", 5000, tvBalance) }
        btnFlipcart.setOnClickListener { redeemReward("Flipkart ₹100 Voucher", 8000, tvBalance) }
        btnMyntra.setOnClickListener { redeemReward("Myntra ₹50 Voucher", 5000, tvBalance) }

        // Hero affiliate banner
        tvBannerTitle.text = "🔥 Deal of the Day"
        imgBanner.setOnClickListener {
            if (FirebaseManager.heroAffiliateLink.isNotEmpty()) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FirebaseManager.heroAffiliateLink)))
            }
        }
    }

    private fun redeemReward(name: String, cost: Int, tvBalance: TextView) {
        FirebaseManager.getUserData(uid) { wallet, _, _ ->
            activity?.runOnUiThread {
                if (wallet < cost) {
                    Toast.makeText(context, "Need ${cost - wallet} more points!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "🎉 $name redeemed! Check your email.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
