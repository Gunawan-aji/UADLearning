package com.uad.uadlearningapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
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

    private var selectedFileUri: Uri? = null
    private val PICK_FILE_REQUEST = 1
    private var tvFileNameRef: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_detail)

        initCloudinary()

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

    private fun initCloudinary() {
        try {
            val config = mapOf(
                "cloud_name" to "dhipf6bhy",
                "api_key" to "223764587583262",
                "api_secret" to "cT2WqtQziYY8_A7DBhTlHq_T4hc"
            )
            MediaManager.init(this, config)
        } catch (e: Exception) {}
    }

    private fun setupAnnouncementRecyclerView(courseId: String, creatorId: String?) {
        val rvAnnouncements = findViewById<RecyclerView>(R.id.rvAnnouncements)
        announcementAdapter = AnnouncementAdapter(announcementList) { announcement ->
            if (announcement.content.contains("[TUGAS BARU]")) {
                val intent = Intent(this, TaskActivity::class.java)
                val rawContent = announcement.content.replace("[TUGAS BARU]: ", "")
                val parts = rawContent.split("\n\n")

                intent.putExtra("TASK_ID", announcement.id)
                intent.putExtra("COURSE_ID", courseId)
                intent.putExtra("CREATOR_ID", creatorId)
                intent.putExtra("TASK_TITLE", parts.getOrNull(0) ?: "Tugas")
                intent.putExtra("TASK_DESC", parts.getOrNull(1) ?: "Tidak ada deskripsi")
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

    private fun showAddTaskDialog(courseId: String) {
        val builder = AlertDialog.Builder(this)
        val dialogView = layoutInflater.inflate(R.layout.layout_add_task, null)

        val etTitle = dialogView.findViewById<EditText>(R.id.etTaskTitle)
        val etDesc = dialogView.findViewById<EditText>(R.id.etTaskDescription)
        val etDeadline = dialogView.findViewById<EditText>(R.id.etTaskDeadline)
        val btnUpload = dialogView.findViewById<Button>(R.id.btnUploadFile)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBarCreateTask)
        tvFileNameRef = dialogView.findViewById(R.id.tvFileName)

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
        builder.setCancelable(false)
        builder.setPositiveButton("Posting", null)
        builder.setNegativeButton("Batal") { dialog, _ ->
            selectedFileUri = null
            dialog.dismiss()
        }

        val alertDialog = builder.create()
        alertDialog.show()

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val title = etTitle.text.toString().trim()
            val desc = etDesc.text.toString().trim()
            val deadline = etDeadline.text.toString().trim()

            if (title.isEmpty()) {
                etTitle.error = "Judul wajib diisi"
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false

            if (selectedFileUri != null) {
                MediaManager.get().upload(selectedFileUri)
                    .option("folder", "materi_tugas/")
                    .option("upload_preset", "tugas_preset")
                    .callback(object : UploadCallback {
                        override fun onStart(requestId: String) {}
                        override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
                        override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                            val secureUrl = resultData["secure_url"].toString()
                            postTaskWithDeadline(courseId, title, desc, deadline, secureUrl)
                            runOnUiThread { alertDialog.dismiss() }
                        }
                        override fun onError(requestId: String, error: ErrorInfo) {
                            runOnUiThread {
                                progressBar.visibility = View.GONE
                                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                                Toast.makeText(this@ClassDetailActivity, "Gagal upload file", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onReschedule(requestId: String, error: ErrorInfo) {}
                    }).dispatch()
            } else {
                postTaskWithDeadline(courseId, title, desc, deadline, null)
                alertDialog.dismiss()
            }
        }
    }

    private fun postTaskWithDeadline(courseId: String, title: String, desc: String, deadline: String, fileUrl: String?) {
        val dbRoot = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

        // PENTING: Ambil satu Key yang sama untuk kedua node
        val taskKey = dbRoot.child("tasks").push().key ?: return

        val userName = auth.currentUser?.displayName ?: "Dosen UAD"
        val currentDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())

        val taskData = mutableMapOf<String, Any?>(
            "id" to taskKey,
            "title" to title,
            "description" to desc,
            "deadline" to deadline,
            "creatorId" to currentUserId.toString()
        )

        // Masukkan fileUrl dan fileName HANYA jika fileUrl tidak null
        if (!fileUrl.isNullOrEmpty()) {
            taskData["fileUrl"] = fileUrl
            taskData["fileName"] = "Materi_$title.pdf"
        }

        // Simpan Detail Tugas Terlebih Dahulu
        dbRoot.child("tasks").child(taskKey).setValue(taskData).addOnSuccessListener {
            // Setelah detail tersimpan, baru buat pengumuman agar mahasiswa bisa klik
            val finalContent = "[TUGAS BARU]: $title\n\n$desc"
            val announcement = Announcement(taskKey, userName, currentDate, finalContent, userName.take(1).uppercase())

            dbRoot.child("announcements").child(courseId).child(taskKey).setValue(announcement).addOnSuccessListener {
                Toast.makeText(this, "Tugas berhasil diposting!", Toast.LENGTH_SHORT).show()
                selectedFileUri = null // Reset URI setelah sukses
            }
        }
    }

    private fun postAnnouncement(courseId: String, content: String) {
        val annRef = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/")
            .getReference("announcements").child(courseId)

        val annId = annRef.push().key ?: return
        val userName = auth.currentUser?.displayName ?: "User UAD"
        val currentDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())

        val newAnnouncement = Announcement(annId, userName, currentDate, content, userName.take(1).uppercase())

        annRef.child(annId).setValue(newAnnouncement).addOnSuccessListener {
            Toast.makeText(this, "Berhasil diposting!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == RESULT_OK) {
            selectedFileUri = data?.data
            tvFileNameRef?.text = "File siap diupload"
        }
    }
}