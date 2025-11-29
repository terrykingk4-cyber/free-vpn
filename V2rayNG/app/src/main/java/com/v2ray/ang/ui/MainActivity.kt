package com.v2ray.ang.ui

import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
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
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.helper.SimpleItemTouchHelperCallback
import com.v2ray.ang.viewmodel.MainViewModel

class MainActivity : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var binding: ActivityMainBinding
    private val mainViewModel: MainViewModel by viewModels()
    private val adapter by lazy { MainRecyclerAdapter(this) }

    // لاجیک اصلی دریافت مجوز VPN (بدون تغییر)
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

        title = getString(R.string.app_name)
        setSupportActionBar(binding.toolbar)

        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        binding.navView.setNavigationItemSelectedListener(this)

        // تنظیمات ریسایکلر ویو (لیست سرورها)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        val callback = SimpleItemTouchHelperCallback(adapter)
        val itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(binding.recyclerView)

        setupViewModel()

        // >>>> شروع کدهای API هندشیک <<<<
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            mainViewModel.startHandshake(this, version)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // مشاهده پاسخ سرور و آپدیت UI
        mainViewModel.apiResponseLiveData.observe(this) { data ->
            if (data != null) {
                // 1. نمایش متن دریافت شده بالای دکمه
                binding.tvApiMessage.text = data.text ?: ""

                // 2. بررسی آپدیت
                if (data.updateNeeded) {
                    showUpdateDialog(data.forceUpdate)
                }
            }
        }
        // >>>> پایان کدهای API <<<<


        // ************ FAB اصلی پروژه (با لاجیک اصلی)  ************
        // نکته: در XML جدید اسم دکمه fabConnect است، اما لاجیک همانی است که خواستی
        binding.fabConnect.setOnClickListener {
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
        // **********************************************************
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
                binding.fabConnect.backgroundTintList =
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.color_fab_active)
            } else {
                binding.fabConnect.backgroundTintList =
                    androidx.core.content.ContextCompat.getColorStateList(this, R.color.color_fab_inactive)
            }
        }
        mainViewModel.startListenBroadcast()
    }

    // تابع نمایش دیالوگ آپدیت
    private fun showUpdateDialog(isForce: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("نسخه جدید موجود است")
        builder.setMessage("لطفا برای عملکرد صحیح برنامه را آپدیت کنید.")
        builder.setPositiveButton("آپدیت") { _, _ ->
            val url = "https://google.com" // لینک دانلود خود را اینجا بگذارید
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            if (isForce) finish()
        }

        if (isForce) {
            builder.setCancelable(false)
        } else {
            builder.setCancelable(true)
            builder.setNegativeButton("بعدا") { dialog, _ ->
                dialog.dismiss()
            }
        }
        builder.show()
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
            R.id.nav_sub_setting -> {
                startActivity(Intent(this, SubSettingActivity::class.java))
            }
            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            R.id.nav_logcat -> {
                startActivity(Intent(this, LogcatActivity::class.java))
            }
        }
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }
}