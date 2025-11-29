package com.v2ray.ang.ui

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.v2ray.ang.AppConfig
import com.v2ray.ang.AppConfig.VPN
import com.v2ray.ang.R
import com.v2ray.ang.databinding.ActivityMainBinding
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.handler.V2RayServiceManager
import com.v2ray.ang.viewmodel.MainViewModel
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels

class MainActivity : AppCompatActivity() {

    private val binding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    val mainViewModel: MainViewModel by viewModels()

    private val requestVpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                startV2Ray()
            }
        }

    // 1. راه‌اندازی بایندینگ (Binding)
    binding = ActivityMainBinding.inflate(layoutInflater)
    val view = binding.root
    setContentView(view)

    // 2. تایتل اکشن‌بار (اختیاری)
    title = getString(R.string.app_name)

    // 3. دریافت ورژن برنامه برای API
    val pInfo = packageManager.getPackageInfo(packageName, 0)
    val version = pInfo.versionName

    // 4. صدا زدن متد هندشیک (ارتباط با سرور)
    // نکته: mainViewModel باید قبلاً تعریف شده باشد (معمولا توسط by viewModels یا ViewModelProvider)
    mainViewModel.startHandshake(this, version)

    // 5. مشاهده (Observe) پاسخ سرور و آپدیت UI
    mainViewModel.apiResponseLiveData.observe(this) { data ->
        if (data != null) {
            // نمایش متن دریافت شده از سرور
            binding.tvApiMessage.text = data.text ?: ""

            // بررسی نیاز به آپدیت
            if (data.updateNeeded) {
                showUpdateDialog(data.forceUpdate)
            }
            
            // اگر کانفیگ جدیدی ایمپورت شده، لیست را رفرش کن
            if (!data.configs.isNullOrEmpty()) {
                mainViewModel.reloadServerList() // متدی برای رفرش لیست (ممکن است نامش در پروژه شما فرق کند)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        //  ************  FAB اصلی پروژه — بدون حتی یک تغییر  ************
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
        // **************************************************************
    }

    private fun startV2Ray() {
        V2RayServiceManager.startVService(this)
    }

    private fun showUpdateDialog(isForce: Boolean) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("نسخه جدید موجود است")
        builder.setMessage("لطفا برای عملکرد صحیح برنامه را آپدیت کنید.")
        builder.setPositiveButton("آپدیت") { _, _ ->
            // لینک دانلود یا هدایت به صفحه آپدیت
            val url = "LINK_TO_DOWNLOAD" 
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            if (isForce) finish() // بستن برنامه اگر اجباری بود و کاربر رفت بیرون
        }

        if (isForce) {
            builder.setCancelable(false) // غیرقابل بستن
        } else {
            builder.setCancelable(true)
            builder.setNegativeButton("بعدا") { dialog, _ ->
                dialog.dismiss()
            }
        }
        builder.show()
    }
}
