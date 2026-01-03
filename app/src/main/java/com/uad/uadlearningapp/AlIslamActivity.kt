package com.uad.uadlearningapp

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.*

class AlIslamActivity : AppCompatActivity() {

    // Simpan status switch agar tidak reset saat pindah activity
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_al_islam)

        sharedPreferences = getSharedPreferences("UAD_ALISLAM_PREF", Context.MODE_PRIVATE)

        val jadwalSholat = listOf(
            Triple("Subuh", 4, 15),
            Triple("Dzuhur", 11, 55),
            Triple("Ashar", 15, 15),
            Triple("Maghrib", 18, 5),
            Triple("Isya", 19, 20)
        )

        // Setup UI Row
        setupSholatRow(findViewById(R.id.rowSubuh), "Subuh", "04:15")
        setupSholatRow(findViewById(R.id.rowDzuhur), "Dzuhur", "11:55")
        setupSholatRow(findViewById(R.id.rowAshar), "Ashar", "15:15")
        setupSholatRow(findViewById(R.id.rowMaghrib), "Maghrib", "18:05")
        setupSholatRow(findViewById(R.id.rowIsya), "Isya", "19:20")

        val switchMain = findViewById<SwitchMaterial>(R.id.switchMainAlarm)

        // AMBIL STATUS TERAKHIR (Penting agar switch tetap ON jika sebelumnya sudah diaktifkan)
        val isAlarmActive = sharedPreferences.getBoolean("IS_ALARM_ACTIVE", false)
        switchMain.isChecked = isAlarmActive

        switchMain.setOnCheckedChangeListener { _, isChecked ->
            // SIMPAN STATUS BARU
            sharedPreferences.edit().putBoolean("IS_ALARM_ACTIVE", isChecked).apply()

            if (isChecked) {
                jadwalSholat.forEachIndexed { index, sholat ->
                    setSholatAlarm(sholat.first, sholat.second, sholat.third, index)
                }
                Toast.makeText(this, "Adzan Otomatis Aktif", Toast.LENGTH_SHORT).show()
            } else {
                for (i in 0..4) {
                    cancelAlarm(i)
                }
                Toast.makeText(this, "Adzan Otomatis Mati", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSholatRow(view: View, name: String, time: String) {
        view.findViewById<TextView>(R.id.tvSholatName).text = name
        view.findViewById<TextView>(R.id.tvSholatTime).text = time
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun setSholatAlarm(name: String, hour: Int, minute: Int, requestCode: Int) {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

            // Handle Izin Android 12+ (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!alarmManager.canScheduleExactAlarms()) {
                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    startActivity(intent)
                    return
                }
            }

            val intent = Intent(this, SholatReceiver::class.java).apply {
                putExtra("SHOLAT_NAME", name)
                action = "com.uad.ACTION_SHOLAT_ALARM"
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (calendar.before(Calendar.getInstance())) {
                calendar.add(Calendar.DATE, 1)
            }

            // Gunakan setExactAndAllowWhileIdle agar alarm tetap bunyi saat HP mode hemat daya (Doze Mode)
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )

        } catch (e: Exception) {
            Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, SholatReceiver::class.java).apply {
            action = "com.uad.ACTION_SHOLAT_ALARM"
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }
}