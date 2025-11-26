package com.v2ray.ang.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.v2ray.ang.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fab = findViewById<FloatingActionButton>(R.id.fab)

        fab.setOnClickListener {
            // اینجا کدی که باید با فشردن دکمه اجرا بشه رو قرار بده
        }
    }
}
