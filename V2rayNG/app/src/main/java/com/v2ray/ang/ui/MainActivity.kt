package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.navigation.NavigationView
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.dto.EConfigType
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.viewmodel.MainViewModel
import com.v2ray.ang.extension.toast

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    val mainViewModel: MainViewModel by viewModels()
    private val adapter by lazy { MainRecyclerAdapter(this) }

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // *** بخش تنظیمات Toolbar و Drawer حذف شد چون هدر را برداشتیم ***
        // فقط لیسنر منوی کشویی باقی می‌ماند (اگر با کشیدن انگشت باز شود)
        binding.navView.setNavigationItemSelectedListener(this)

        // تنظیمات لیست (هرچند مخفی است، اما برای عملکرد آداپتور لازم است)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        setupViewModel()

        // دریافت کانفیگ از API
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName ?: "1.0.0"
            mainViewModel.startHandshake(this, version)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // مشاهده پاسخ سرور
        mainViewModel.apiResponseLiveData.observe(this) { data ->
            if (data != null) {
                binding.tvApiMessage.text = data.text ?: ""
                if (data.updateNeeded) {
                    showUpdateDialog(data.forceUpdate)
                }
            }
        }

        // دکمه اتصال (FAB)
        binding.fab.setOnClickListener {
            if (mainViewModel.isRunning.value == true) {
                V2RayServiceManager.stopVService(this)
            } else if ((MmkvManager.decodeSettingsString(AppConfig.PREF_MODE) ?: VPN) == VPN) {
                val intent = VpnService.prepare(this)
                if (intent == null) {
                    startV2Ray()
                } else {
                    requestVpnPermission.launch(intent)
                }
            } else {
                startV2Ray()
            }
        }
    }

    private fun startV2Ray() {
        V2RayServiceManager.startVService(this)
    }

    private fun setupViewModel() {
        mainViewModel.updateListAction.observe(this) { index ->
            if (index >= 0) {
                adapter.notifyItemChanged(index)
            } else {
                adapter.notifyDataSetChanged()
            }
        }
        mainViewModel.isRunning.observe(this) { isRunning ->
            adapter.isRunning = isRunning
            adapter.notifyDataSetChanged()
            if (isRunning) {
                binding.fab.setImageResource(R.drawable.ic_disconnect_btn)
            } else {
                binding.fab.setImageResource(R.drawable.ic_connect_btn)
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

    // گزینه‌های منو
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        // مخفی کردن تمام گزینه‌های ورود دستی
        menu.findItem(R.id.import_qrcode)?.isVisible = false
        menu.findItem(R.id.import_clipboard)?.isVisible = false
        menu.findItem(R.id.import_manual_vmess)?.isVisible = false
        menu.findItem(R.id.import_manual_vless)?.isVisible = false
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
                startV2Ray()
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