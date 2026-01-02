package com.uad.uadlearningapp

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // 1. Auto Login Check
        if (auth.currentUser != null) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        // 2. Inisialisasi View
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvToRegister = findViewById<TextView>(R.id.tvToRegister)
        val btnShowPassword = findViewById<ImageButton>(R.id.btnShowPassword)

        var isPasswordVisible = false

        // 3. Logika Show/Hide Password
        btnShowPassword.setOnClickListener {
            if (isPasswordVisible) {
                // Sembunyikan Password
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnShowPassword.setImageResource(android.R.drawable.ic_menu_view)
                isPasswordVisible = false
            } else {
                // Tampilkan Password
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                // Gunakan icon yang sesuai, misal ic_menu_close_clear_cancel sebagai representasi 'hide'
                btnShowPassword.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                isPasswordVisible = true
            }
            // Pastikan kursor tetap di posisi paling belakang teks
            etPassword.setSelection(etPassword.text.length)
        }

        // 4. Logika Login
        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email dan Password harus diisi", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Selamat Datang!", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this, "Gagal Login: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // 5. Navigasi ke Register
        tvToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}