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
import androidx.recyclerview.widget.ItemTouchHelper // اصلاح ۱: اضافه شدن ایمپورت
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

    // متغیر برای جلوگیری از تداخل کلیک‌ها هنگام اجرای انیمیشن
    private var isFakeConnectingAnimationRunning = false
    private var spinnerAnimator: ObjectAnimator? = null

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

        // فقط لیسنر منوی کشویی باقی می‌ماند
        binding.navView.setNavigationItemSelectedListener(this)

        // تنظیمات لیست (هرچند مخفی است)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        setupViewModel()

        // دریافت کانفیگ از API و نمایش ورژن
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

        // کلیک روی دکمه اتصال (شروع پروسه هوشمند)
        binding.fab.setOnClickListener {
            // اگر انیمیشن در حال اجراست، کلیک را نادیده بگیر
            if (isFakeConnectingAnimationRunning) return@setOnClickListener

            if (mainViewModel.isRunning.value == true) {
                // اگر متصل است -> قطع کن (بدون انیمیشن خاص)
                V2RayServiceManager.stopVService(this)
            } else {
                // اگر قطع است -> شروع پروسه اتصال هوشمند
                handleSmartConnect()
            }
        }
    }

    /**
     * لاجیک اتصال هوشمند:
     * ۱. شروع انیمیشن
     * ۲. پینگ گرفتن از همه سرورها
     * ۳. انتخاب بهترین سرور
     * ۴. اتصال
     */
    private fun handleSmartConnect() {
        lifecycleScope.launch {
            // 1. شروع انیمیشن ظاهری (بزرگ شدن و چرخش)
            animateConnectStart()

            // اجرای عملیات سنگین در ترد IO
            withContext(Dispatchers.IO) {
                // 2. شروع تست پینگ همه سرورها
                // از testAllTcping استفاده میکنیم که سریعتر و دقیقتر برای انتخاب سرور است
                mainViewModel.testAllTcping()

                // 3. صبر کردن برای آمدن نتایج (3 ثانیه)
                // این مدت زمان با انیمیشن هماهنگ است
                delay(3000)

                // 4. مرتب‌سازی سرورها بر اساس نتیجه پینگ (بهترین پینگ می‌آید اول لیست)
                mainViewModel.sortByTestResults()
            }
            
            // اصلاح ۲: رفرش لیست به ترد اصلی (بیرون از withContext) منتقل شد تا کرش نکند
            mainViewModel.reloadServerList()

            // 5. انتخاب بهترین سرور (اولین آیتم لیست بعد از سورت)
            val bestServer = mainViewModel.serversCache.firstOrNull()
            if (bestServer != null) {
                // ست کردن سرور انتخاب شده در MMKV
                MmkvManager.setSelectServer(bestServer.guid)
            }

            // 6. درخواست مجوز VPN و اتصال نهایی
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
        // پایان انیمیشن و تغییر آیکون به حالت متصل
        animateConnectEnd(targetIsConnected = true)
    }

    // >>>> بخش انیمیشن‌ها <<<<

    private fun animateConnectStart() {
        isFakeConnectingAnimationRunning = true
        // نمایش خطوط چرخان
        binding.loadingSpinner.visibility = View.VISIBLE
        binding.loadingSpinner.alpha = 0f
        binding.loadingSpinner.animate().alpha(1f).setDuration(300).start()

        // شروع انیمیشن چرخش (Rotation) روی ImageView
        spinnerAnimator = ObjectAnimator.ofFloat(binding.loadingSpinner, "rotation", 0f, 360f).apply {
            duration = 1000 // سرعت چرخش (هر دور 1 ثانیه)
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator() // چرخش یکنواخت
            start()
        }

        // انیمیشن دکمه (بزرگ شدن و بالا رفتن)
        binding.fab.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .translationY(-150f)
            .setDuration(500)
            .setInterpolator(androidx.interpolator.view.animation.FastOutSlowInInterpolator())
            .start()
    }

    private fun animateConnectEnd(targetIsConnected: Boolean) {
        // توقف چرخش
        spinnerAnimator?.cancel()
        
        // فید اوت و مخفی کردن اسپینر
        binding.loadingSpinner.animate()
            .alpha(0f)
            .setDuration(300)
            .withEndAction { 
                binding.loadingSpinner.visibility = View.GONE 
                binding.loadingSpinner.rotation = 0f // ریست کردن زاویه
            }
            .start()

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
            // فقط اگر انیمیشن در حال اجرا نیست آیکون را عوض کن
            // (چون در حین انیمیشن نمیخواهیم آیکون بپرد)
            if (!isFakeConnectingAnimationRunning) {
                updateFabIcon(isRunning)
            } else if (!isRunning) {
                // اگر وسط انیمیشن اتصال قطع شد (ارور)، انیمیشن را تمام کن
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