package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
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
import com.v2ray.ang.util.Utils
import com.v2ray.ang.viewmodel.MainViewModel

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    // نکته مهم: این متغیر باید public باشد (بدون private)
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

        // 1. تنظیمات تولبار و عنوان
        title = getString(R.string.app_name)
        setSupportActionBar(binding.toolbar)

        // 2. تنظیمات منوی کشویی (Drawer)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        // 3. تنظیمات لیست سرورها (RecyclerView)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        // 4. ستاپ کردن ویومدل
        setupViewModel()

        // 5. اجرای لاجیک API
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            // جلوگیری از کرش اگر ورژن نال بود
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

        //  ************ FAB اصلی پروژه (با شناسه fab)  ************
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
                binding.fab.backgroundTintList =
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.color_fab_active)
            } else {
                binding.fab.backgroundTintList =
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.color_fab_inactive)
            }
        }
        mainViewModel.startListenBroadcast()
    }

    private fun showUpdateDialog(isForce: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("نسخه جدید موجود است")
        builder.setMessage("لطفا برنامه را آپدیت کنید.")
        builder.setPositiveButton("آپدیت") { _, _ ->
            val url = "https://google.com" // لینک دانلود
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
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.import_qrcode -> {
                importQRcode(true)
                true
            }
            R.id.import_clipboard -> {
                importClipboard()
                true
            }
            R.id.import_manual_vmess -> {
                importManual(EConfigType.VMESS)
                true
            }
            R.id.import_manual_vless -> {
                importManual(EConfigType.VLESS)
                true
            }
            // سایر آیتم‌های منو
            R.id.sub_update -> {
                importConfigViaSub()
                true
            }
            R.id.export_all -> {
                if (mainViewModel.exportAllServer() > 0) {
                    Utils.toast(R.string.toast_success, this)
                } else {
                    Utils.toast(R.string.toast_failure, this)
                }
                true
            }
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
            R.id.del_all_config -> {
                AlertDialog.Builder(this).setMessage(R.string.del_config_comfirm)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mainViewModel.removeAllServer()
                        mainViewModel.reloadServerList()
                    }
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun importQRcode(forConfig: Boolean): Boolean {
        startActivity(Intent(this, ScannerActivity::class.java))
        return true
    }
    
    fun importClipboard() {
        if (mainViewModel.importClipboard(this)) {
             Utils.toast(R.string.toast_success, this)
             mainViewModel.reloadServerList()
        } else {
             Utils.toast(R.string.toast_failure, this)
        }
    }

    fun importConfigViaSub() {
        // لاجیک ساده برای آپدیت سابسکرایبشن
        mainViewModel.updateConfigViaSubAll()
    }

    fun importManual(type: EConfigType) {
        val intent = Intent().putExtra("createConfigType", type.value)
            .setClass(this, ServerActivity::class.java)
        startActivity(intent)
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
            R.id.nav_sub_setting -> startActivity(Intent(this, SubSettingActivity::class.java))
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_logcat -> startActivity(Intent(this, LogcatActivity::class.java))
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}