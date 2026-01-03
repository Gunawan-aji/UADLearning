package com.uad.uadlearningapp

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.uad.uadlearningapp.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var courseAdapter: CourseAdapter
    private var courseDataList = mutableListOf<Course>()

    private val auth = FirebaseAuth.getInstance()
    private val dbRef = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/").reference
    private val courseRef = dbRef.child("courses")
    private val memberRef = dbRef.child("members")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Inisialisasi awal
        setupRecyclerView()
        fetchMyCourses()

        // --- FITUR BARU: Update Ringkasan Tugas ---
        updateTaskSummary()

        // 2. Setup Navigasi Drawer & Menu
        setupNavigationDrawer()

        // 3. Info Akun
        binding.btnAccount.setOnClickListener {
            showAccountInfoDialog()
        }

        // 4. FAB dengan Pilihan
        binding.fabAdd.setOnClickListener {
            showActionDialog()
        }
    }

    // --- LOGIKA: RINGKASAN TUGAS (Mendekati Deadline & Belum Dikerjakan) ---
    private fun updateTaskSummary() {
        val currentUid = auth.currentUser?.uid ?: return

        dbRef.child("tasks").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val tasksToDo = mutableListOf<String>()
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val now = Date()

                for (taskSnap in snapshot.children) {
                    val taskId = taskSnap.key ?: ""
                    val title = taskSnap.child("title").value?.toString() ?: "Tugas"
                    val deadlineStr = taskSnap.child("deadline").value?.toString() ?: ""
                    val creatorId = taskSnap.child("creatorId").value?.toString() ?: ""

                    // VALIDASI 1: Jika lu adalah pembuat tugas (Dosen), lompati/jangan tampilkan peringatan
                    if (currentUid == creatorId) {
                        continue
                    }

                    // VALIDASI 2: Cek apakah mahasiswa sudah mengumpulkan
                    dbRef.child("submissions").child(taskId).child(currentUid)
                        .addListenerForSingleValueEvent(object : ValueEventListener {
                            override fun onDataChange(subSnap: DataSnapshot) {
                                // JIKA BELUM ADA DATA SUBMISSION
                                if (!subSnap.exists()) {
                                    try {
                                        val deadlineDate = sdf.parse(deadlineStr)
                                        if (deadlineDate != null) {
                                            val diff = deadlineDate.time - now.time
                                            val hours = diff / (1000 * 60 * 60)

                                            // Tampilkan jika deadline < 48 jam dan belum lewat
                                            if (hours in 0..48) {
                                                tasksToDo.add(title)
                                            }
                                        }
                                    } catch (e: Exception) {}
                                }

                                // Update Tampilan Card secara visual
                                if (tasksToDo.isNotEmpty()) {
                                    binding.tvTaskSummary.text = "Segera kerjakan: ${tasksToDo.joinToString(", ")}"
                                    binding.tvTaskSummary.setTextColor(Color.RED)
                                } else {
                                    binding.tvTaskSummary.text = "Tidak ada tugas yang perlu dikerjakan segera"
                                    binding.tvTaskSummary.setTextColor(Color.parseColor("#757575"))
                                }
                            }
                            override fun onCancelled(error: DatabaseError) {}
                        })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun setupNavigationDrawer() {
        binding.toolbarTask.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_kelas -> binding.drawerLayout.closeDrawer(GravityCompat.START)
                R.id.menu_kalender -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://baa.uad.ac.id/kalender-akademik/")))
                }
                R.id.menu_islam -> {
                    // NAVIGASI KE HALAMAN AL-ISLAM
                    startActivity(Intent(this, AlIslamActivity::class.java))
                }
                R.id.menu_setting -> {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_kelas -> binding.drawerLayout.closeDrawer(GravityCompat.START)

                R.id.menu_kalender -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://baa.uad.ac.id/kalender-akademik/")))
                }

                R.id.menu_islam -> {
                    startActivity(Intent(this, AlIslamActivity::class.java))
                }

                // --- FITUR BARU: LAPOR BSI VIA WHATSAPP ---
                R.id.menu_pesan -> {
                    val nomorBsi = "6282282691960" // nomor WA BSI (Gunakan format 62, bukan 0)
                    val pesan = "Halo BSI UAD, saya ingin melaporkan kendala terkait sistem..."
                    val url = "https://api.whatsapp.com/send?phone=$nomorBsi&text=${Uri.encode(pesan)}"

                    try {
                        val intent = Intent(Intent.ACTION_VIEW)
                        intent.data = Uri.parse(url)
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "WhatsApp tidak terinstall", Toast.LENGTH_SHORT).show()
                    }
                }

                R.id.menu_setting -> {
                    startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupRecyclerView() {
        courseAdapter = CourseAdapter(
            courseList = courseDataList,
            onDeleteClick = { courseId -> confirmDeleteCourse(courseId) },
            onItemClick = { course ->
                val intent = Intent(this, ClassDetailActivity::class.java)
                intent.putExtra("COURSE_ID", course.id)
                intent.putExtra("COURSE_NAME", course.courseName)
                intent.putExtra("BANNER_COLOR", course.bannerColor)
                intent.putExtra("CREATOR_ID", course.creatorId)
                intent.putExtra("CLASS_CODE", course.classCode)
                startActivity(intent)
            }
        )
        binding.rvTasks.layoutManager = LinearLayoutManager(this)
        binding.rvTasks.adapter = courseAdapter
    }

    private fun fetchMyCourses() {
        val currentUid = auth.currentUser?.uid ?: return
        memberRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val myCourseIds = mutableListOf<String>()
                for (classSnap in snapshot.children) {
                    if (classSnap.hasChild(currentUid)) {
                        myCourseIds.add(classSnap.key ?: "")
                    }
                }
                loadCourseDetails(myCourseIds)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadCourseDetails(ids: List<String>) {
        courseRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                courseDataList.clear()
                for (id in ids) {
                    val data = snapshot.child(id)
                    val course = data.getValue(Course::class.java)
                    course?.let {
                        it.id = data.key
                        courseDataList.add(it)
                    }
                }
                binding.tvEmptyState.visibility = if (courseDataList.isEmpty()) View.VISIBLE else View.GONE
                courseAdapter.updateData(courseDataList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showActionDialog() {
        val options = arrayOf("Buat Kelas Baru (Dosen)", "Gabung ke Kelas (Mahasiswa)")
        AlertDialog.Builder(this)
            .setTitle("Opsi Kelas")
            .setItems(options) { _, which ->
                if (which == 0) showAddCourseDialog() else showJoinClassDialog()
            }
            .show()
    }

    private fun showAddCourseDialog() {
        val builder = AlertDialog.Builder(this)
        val dialogLayout = layoutInflater.inflate(R.layout.layout_add_course, null)
        val etName = dialogLayout.findViewById<EditText>(R.id.etCourseName)
        val etLecturer = dialogLayout.findViewById<EditText>(R.id.etLecturer)
        val etSchedule = dialogLayout.findViewById<EditText>(R.id.etSchedule)

        builder.setView(dialogLayout)
        builder.setTitle("Buat Kelas Baru")
        builder.setPositiveButton("Buat") { _, _ ->
            val name = etName.text.toString().trim()
            val lecturer = etLecturer.text.toString().trim()
            val schedule = etSchedule.text.toString().trim()

            if (name.isNotEmpty()) {
                val id = courseRef.push().key ?: return@setPositiveButton
                val currentUid = auth.currentUser?.uid ?: ""
                val classCode = UUID.randomUUID().toString().substring(0, 6).uppercase()
                val colors = listOf("#1A73E8", "#1E8E3E", "#F9AB00", "#D93025", "#8E24AA")
                val newCourse = Course(id, name, lecturer, schedule, colors.random(), currentUid, classCode)

                courseRef.child(id).setValue(newCourse)
                memberRef.child(id).child(currentUid).setValue(true)
                Toast.makeText(this, "Kelas berhasil dibuat! Kode: $classCode", Toast.LENGTH_LONG).show()
            }
        }
        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    private fun showJoinClassDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Gabung Kelas")
        val input = EditText(this)
        input.hint = "Masukkan 6 Digit Kode Kelas"
        builder.setView(input)

        builder.setPositiveButton("Gabung") { _, _ ->
            val code = input.text.toString().trim().uppercase()
            if (code.length == 6) joinByCode(code)
            else Toast.makeText(this, "Kode harus 6 karakter", Toast.LENGTH_SHORT).show()
        }
        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    private fun joinByCode(code: String) {
        val currentUid = auth.currentUser?.uid ?: return
        courseRef.orderByChild("classCode").equalTo(code)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (data in snapshot.children) {
                            val courseId = data.key ?: ""
                            memberRef.child(courseId).child(currentUid).setValue(true)
                            Toast.makeText(this@MainActivity, "Berhasil bergabung!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@MainActivity, "Kode salah/tidak ditemukan", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun confirmDeleteCourse(courseId: String) {
        AlertDialog.Builder(this)
            .setTitle("Hapus/Keluar")
            .setMessage("Anda akan menghapus akses ke kelas ini. Lanjutkan?")
            .setPositiveButton("Ya") { _, _ ->
                val currentUid = auth.currentUser?.uid ?: ""
                memberRef.child(courseId).child(currentUid).removeValue()
                Toast.makeText(this, "Akses kelas dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showAccountInfoDialog() {
        val user = auth.currentUser
        AlertDialog.Builder(this)
            .setTitle("Informasi Akun")
            .setMessage("Nama: ${user?.displayName}\nEmail: ${user?.email}\nID: ${user?.uid}")
            .setPositiveButton("OK", null)
            .setNegativeButton("Logout") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }.show()
    }

    @SuppressLint("GestureBackNavigation")
    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}