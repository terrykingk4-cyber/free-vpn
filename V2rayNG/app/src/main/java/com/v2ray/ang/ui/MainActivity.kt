package com.v2ray.ang.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.extension.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    val mainViewModel: MainViewModel by viewModels()
    private val adapter by lazy { MainRecyclerAdapter(this) }

    private var isFakeConnectingAnimationRunning = false
    
    // انیماتورهای جداگانه برای هر حلقه
    private var animSpinner1: ObjectAnimator? = null
    private var animSpinner2: ObjectAnimator? = null
    private var animSpinner3: ObjectAnimator? = null

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2RayWithDelay()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navView.setNavigationItemSelectedListener(this)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        setupViewModel()

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName ?: "1.0.0"
            binding.tvVersion.text = "Ver: $version"
            mainViewModel.startHandshake(this, version)
        } catch (e: Exception) {
            e.printStackTrace()
            binding.tvVersion.text = "Ver: Unknown"
        }

        mainViewModel.apiResponseLiveData.observe(this) { data ->
            if (data != null) {
                binding.tvApiMessage.text = data.text ?: ""
                if (data.updateNeeded) {
                    showUpdateDialog(data.forceUpdate)
                }
            }
        }

        binding.fab.setOnClickListener {
            if (isFakeConnectingAnimationRunning) return@setOnClickListener

            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else {
                handleSmartConnect()
            }
        }
    }

    private fun handleSmartConnect() {
        lifecycleScope.launch {
            // 1. شروع انیمیشن (بالا رفتن و چرخش)
            animateConnectStart()

            withContext(Dispatchers.IO) {
                mainViewModel.testAllTcping()
                delay(3000)
                mainViewModel.sortByTestResults()
            }
            
            mainViewModel.reloadServerList()

            val bestServer = mainViewModel.serversCache.firstOrNull()
            if (bestServer != null) {
                MmkvManager.setSelectServer(bestServer.guid)
            }

            if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                val intent = VpnService.prepare(this@MainActivity)
                if (intent == null) {
                    startV2RayWithDelay()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2RayWithDelay()
            }
        }
    }

    private fun startV2RayWithDelay() {
        V2RayServiceManager.startVService(this)
        animateConnectEnd(targetIsConnected = true)
    }

    // >>>> بخش انیمیشن‌های پیشرفته <<<<

    private fun animateConnectStart() {
        isFakeConnectingAnimationRunning = true
        
        // نمایش حلقه‌ها
        binding.spinner1.visibility = View.VISIBLE
        binding.spinner2.visibility = View.VISIBLE
        binding.spinner3.visibility = View.VISIBLE
        
        binding.spinner1.alpha = 0f
        binding.spinner2.alpha = 0f
        binding.spinner3.alpha = 0f
        
        binding.spinner1.animate().alpha(1f).setDuration(300).start()
        binding.spinner2.animate().alpha(1f).setDuration(300).start()
        binding.spinner3.animate().alpha(1f).setDuration(300).start()

        // تنظیم انیمیشن چرخش متفاوت برای هر حلقه (غیر همزمان)
        
        // حلقه ۱: سرعت معمولی
        animSpinner1 = ObjectAnimator.ofFloat(binding.spinner1, "rotation", 0f, 360f).apply {
            duration = 1200
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // حلقه ۲: سرعت کمتر و جهت معکوس (از 360 به 0)
        animSpinner2 = ObjectAnimator.ofFloat(binding.spinner2, "rotation", 360f, 0f).apply {
            duration = 1800
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // حلقه ۳: سرعت خیلی زیاد
        animSpinner3 = ObjectAnimator.ofFloat(binding.spinner3, "rotation", 0f, 360f).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }

        // حرکت به بالا (شناور شدن) - همه با هم
        val translationY = -150f
        val durationMove = 500L
        val interpolatorMove = androidx.interpolator.view.animation.FastOutSlowInInterpolator()

        binding.fab.animate().translationY(translationY).scaleX(1.2f).scaleY(1.2f).setDuration(durationMove).setInterpolator(interpolatorMove).start()
        
        // حلقه‌ها هم باید با دکمه بالا بیایند
        binding.spinner1.animate().translationY(translationY).setDuration(durationMove).setInterpolator(interpolatorMove).start()
        binding.spinner2.animate().translationY(translationY).setDuration(durationMove).setInterpolator(interpolatorMove).start()
        binding.spinner3.animate().translationY(translationY).setDuration(durationMove).setInterpolator(interpolatorMove).start()
    }

    private fun animateConnectEnd(targetIsConnected: Boolean) {
        // توقف چرخش‌ها
        animSpinner1?.cancel()
        animSpinner2?.cancel()
        animSpinner3?.cancel()
        
        // فید اوت و مخفی کردن حلقه‌ها
        val hideAction = Runnable { 
            binding.spinner1.visibility = View.GONE
            binding.spinner2.visibility = View.GONE
            binding.spinner3.visibility = View.GONE
        }
        
        binding.spinner1.animate().alpha(0f).setDuration(300).start()
        binding.spinner2.animate().alpha(0f).setDuration(300).start()
        binding.spinner3.animate().alpha(0f).setDuration(300).withEndAction(hideAction).start()

        // بازگشت حلقه‌ها به پایین (برای اینکه اگر دوباره انیمیشن اجرا شد از جای درست شروع شود)
        binding.spinner1.animate().translationY(0f).setDuration(500).start()
        binding.spinner2.animate().translationY(0f).setDuration(500).start()
        binding.spinner3.animate().translationY(0f).setDuration(500).start()

        // بازگشت دکمه به جای اول
        binding.fab.animate()
            .scaleX(1.0f)
            .scaleY(1.0f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    isFakeConnectingAnimationRunning = false
                    updateFabIcon(targetIsConnected)
                    binding.fab.animate().setListener(null)
                }
            })
            .start()
    }

    private fun updateFabIcon(isRunning: Boolean) {
        if (isRunning) {
            binding.fab.setImageResource(R.drawable.ic_disconnect_btn)
        } else {
            binding.fab.setImageResource(R.drawable.ic_connect_btn)
        }
    }
    // >>>> پایان انیمیشن‌ها <<<<

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) adapter.notifyItemChanged(index) else adapter.notifyDataSetChanged()
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            adapter.notifyDataSetChanged()
            
            if (!isFakeConnectingAnimationRunning) {
                updateFabIcon(isRunning)
            } else if (!isRunning) {
                animateConnectEnd(false)
            }
        }
        mainViewModel.startListenBroadcast()
    }

    private fun showUpdateDialog(isForce: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("نسخه جدید موجود است")
        builder.setMessage("لطفا برنامه را آپدیت کنید.")
        builder.setPositiveButton("آپدیت") { _, _ ->
            val url = "https://google.com"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            if (isForce) finish()
        }
        if (isForce) {
            builder.setCancelable(false)
        } else {
            builder.setCancelable(true)
            builder.setNegativeButton("بعدا") { dialog, _ -> dialog.dismiss() }
        }
        builder.show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        menu.findItem(R.id.import_qrcode)?.isVisible = false
        menu.findItem(R.id.import_clipboard)?.isVisible = false
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.ping_all -> {
                mainViewModel.testAllTcping()
                true
            }
            R.id.real_ping_all -> {
                mainViewModel.testAllRealPing()
                true
            }
            R.id.service_restart -> {
                V2RayServiceManager.stopVService(this)
                startV2RayWithDelay()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sub_setting -> startActivity(Intent(this, SubSettingActivity::class.java))
            R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.logcat -> startActivity(Intent(this, LogcatActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}