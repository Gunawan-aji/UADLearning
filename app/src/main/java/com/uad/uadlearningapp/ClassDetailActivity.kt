package com.uad.uadlearningapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class ClassDetailActivity : AppCompatActivity() {

    private lateinit var announcementAdapter: AnnouncementAdapter
    private var announcementList = mutableListOf<Announcement>()
    private lateinit var database: DatabaseReference
    private val auth = FirebaseAuth.getInstance()
    private val currentUserId = auth.currentUser?.uid

    private var selectedFileUri: android.net.Uri? = null
    private val PICK_FILE_REQUEST = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_detail)

        val courseId = intent.getStringExtra("COURSE_ID") ?: ""
        val courseName = intent.getStringExtra("COURSE_NAME") ?: "Kelas"
        val bannerColor = intent.getStringExtra("BANNER_COLOR") ?: "#1A73E8"
        val creatorId = intent.getStringExtra("CREATOR_ID")
        val classCode = intent.getStringExtra("CLASS_CODE") ?: "------"

        val toolbar = findViewById<Toolbar>(R.id.toolbarDetail)
        val tvTitle = findViewById<TextView>(R.id.tvDetailCourseName)
        val tvClassCode = findViewById<TextView>(R.id.tvClassCode)
        val layoutBanner = findViewById<RelativeLayout>(R.id.layoutBanner)
        val btnAnnounce = findViewById<MaterialCardView>(R.id.btnNewAnnouncement)
        val fabAddTask = findViewById<FloatingActionButton>(R.id.fabAddTask)

        tvTitle.text = courseName
        try {
            layoutBanner.setBackgroundColor(Color.parseColor(bannerColor))
        } catch (e: Exception) {
            layoutBanner.setBackgroundColor(Color.parseColor("#1A73E8"))
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "UAD Learning"
        toolbar.setNavigationOnClickListener { finish() }

        if (currentUserId != null && currentUserId == creatorId) {
            fabAddTask.visibility = View.VISIBLE
            tvClassCode.visibility = View.VISIBLE
            tvClassCode.text = "Kode Kelas: $classCode"
            supportActionBar?.subtitle = "Kode: $classCode"

            tvClassCode.setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Class Code", classCode)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Kode $classCode disalin!", Toast.LENGTH_SHORT).show()
            }
        } else {
            fabAddTask.visibility = View.GONE
            tvClassCode.visibility = View.GONE
        }

        setupAnnouncementRecyclerView(courseId, creatorId)

        btnAnnounce.setOnClickListener { showAddAnnouncementDialog(courseId) }
        fabAddTask.setOnClickListener { showAddTaskDialog(courseId) }
    }

    private fun setupAnnouncementRecyclerView(courseId: String, creatorId: String?) {
        val rvAnnouncements = findViewById<RecyclerView>(R.id.rvAnnouncements)

        announcementAdapter = AnnouncementAdapter(announcementList) { announcement ->
            if (announcement.content.contains("[TUGAS BARU]")) {
                val intent = Intent(this, TaskActivity::class.java)

                val rawContent = announcement.content.replace("[TUGAS BARU]: ", "")
                val parts = rawContent.split("\n\n")
                val title = parts.getOrNull(0) ?: "Tugas"
                val desc = parts.getOrNull(1) ?: "Tidak ada deskripsi"

                intent.putExtra("TASK_ID", announcement.id)
                intent.putExtra("COURSE_ID", courseId)
                intent.putExtra("CREATOR_ID", creatorId)
                intent.putExtra("TASK_TITLE", title)
                intent.putExtra("TASK_DESC", desc)
                startActivity(intent)
            }
        }

        rvAnnouncements.layoutManager = LinearLayoutManager(this)
        rvAnnouncements.adapter = announcementAdapter

        database = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("announcements").child(courseId)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                announcementList.clear()
                for (data in snapshot.children) {
                    val item = data.getValue(Announcement::class.java)
                    item?.let {
                        it.id = data.key
                        announcementList.add(it)
                    }
                }
                announcementList.reverse()
                announcementAdapter.updateData(announcementList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showAddAnnouncementDialog(courseId: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_add_announcement, null)
        val etContent = dialogView.findViewById<EditText>(R.id.etAnnouncementContent)

        builder.setView(dialogView)
        builder.setTitle("Buat Pengumuman")
        builder.setPositiveButton("Posting") { _, _ ->
            val content = etContent.text.toString().trim()
            if (content.isNotEmpty()) postAnnouncement(courseId, content)
        }
        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    // --- PERBAIKAN: Dialog Tambah Tugas dengan Deadline ---
    private fun showAddTaskDialog(courseId: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_add_task, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etTaskDescription)
        val etDeadline = dialogView.findViewById<EditText>(R.id.etTaskDeadline) // ID dari XML baru
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadFile)

        // Logika Klik Deadline (DatePicker & TimePicker)
        etDeadline.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                val dateSelected = String.format("%d-%02d-%02d", year, month + 1, day)
                TimePickerDialog(this, { _, hour, minute ->
                    val timeSelected = String.format("%02d:%02d", hour, minute)
                    etDeadline.setText("$dateSelected $timeSelected")
                }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnUpload.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            startActivityForResult(intent, PICK_FILE_REQUEST)
        }

        builder.setView(dialogView)
        builder.setTitle("Buat Tugas Baru")
        builder.setPositiveButton("Posting") { _, _ ->
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val deadline = etDeadline.text.toString().trim()

            if (title.isNotEmpty()) {
                // Simpan Tugas & Posting ke Pengumuman
                val finalContent = "[TUGAS BARU]: $title\n\n$desc"
                postTaskWithDeadline(courseId, title, desc, deadline, finalContent)
            } else {
                Toast.makeText(this, "Judul tidak boleh kosong", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Batal", null)
        builder.show()
    }

    // Fungsi Baru untuk menyimpan data tugas secara lengkap ke dua tempat (Node Tasks & Announcements)
    private fun postTaskWithDeadline(courseId: String, title: String, desc: String, deadline: String, content: String) {
        val dbRoot = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

        // 1. Dapatkan Key baru yang sama untuk Task dan Announcement
        val newKey = dbRoot.child("announcements").child(courseId).push().key ?: return

        val userName = auth.currentUser?.displayName ?: "Dosen UAD"
        val currentDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())

        // 2. Simpan ke Node "tasks" untuk data detail (agar TaskActivity bisa baca deadline)
        val taskData = mapOf(
            "id" to newKey,
            "title" to title,
            "description" to desc,
            "deadline" to deadline,
            "creatorId" to currentUserId.toString()
        )
        dbRoot.child("tasks").child(newKey).setValue(taskData)

        // 3. Simpan ke Node "announcements" agar muncul di timeline
        val announcement = Announcement(newKey, userName, currentDate, content, userName.take(1).uppercase())
        dbRoot.child("announcements").child(courseId).child(newKey).setValue(announcement).addOnSuccessListener {
            Toast.makeText(this, "Tugas berhasil diposting!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun postAnnouncement(courseId: String, content: String) {
        val annRef = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("announcements").child(courseId)

        val annId = annRef.push().key ?: return
        val userName = auth.currentUser?.displayName ?: "User UAD"
        val initial = userName.take(1).uppercase()
        val currentDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())

        val newAnnouncement = Announcement(annId, userName, currentDate, content, initial)

        annRef.child(annId).setValue(newAnnouncement).addOnSuccessListener {
            Toast.makeText(this, "Berhasil diposting!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFileUri = data?.data
            Toast.makeText(this, "File siap diupload!", Toast.LENGTH_SHORT).show()
        }
    }
}