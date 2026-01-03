package com.uad.uadlearningapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputType
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.text.SimpleDateFormat
import java.util.*

class TaskActivity : AppCompatActivity() {

    private lateinit var taskId: String
    private lateinit var courseId: String
    private lateinit var role: String

    private lateinit var submissionAdapter: SubmissionAdapter
    private val submissionList = mutableListOf<Map<String, Any>>()
    private lateinit var commentAdapter: CommentAdapter
    private val commentList = mutableListOf<Comment>()

    private var selectedFileUri: Uri? = null
    private val PICK_FILE_REQUEST = 101

    private val auth = FirebaseAuth.getInstance()
    private val dbRef = FirebaseDatabase.getInstance("https://uadlearning-default-rtdb.asia-southeast1.firebasedatabase.app/").reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task)

        // --- 1. INISIALISASI CLOUDINARY ---
        initCloudinary()

        // 2. Setup UI Dasar
        val toolbar = findViewById<Toolbar>(R.id.toolbarTask)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detail Tugas"
        toolbar.setNavigationOnClickListener { finish() }

        taskId = intent.getStringExtra("TASK_ID") ?: ""
        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Detail Tugas"
        val taskDesc = intent.getStringExtra("TASK_DESC") ?: "Tidak ada deskripsi"
        val creatorId = intent.getStringExtra("CREATOR_ID")

        findViewById<TextView>(R.id.tvTaskTitleDetail).text = taskTitle
        findViewById<TextView>(R.id.tvTaskDescriptionDetail).text = taskDesc

        val tvDeadline = findViewById<TextView>(R.id.tvTaskDeadline)
        val btnDownloadMateri = findViewById<Button>(R.id.btnDownloadMateri)

        // --- PERBAIKAN: Menggunakan addValueEventListener agar File Muncul Real-time ---
        dbRef.child("tasks").child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val deadline = snapshot.child("deadline").value?.toString() ?: "Tidak ada batas waktu"
                    tvDeadline.text = "Batas Waktu: $deadline"
                    checkDeadlineLogic(deadline)

                    // Mengambil URL materi yang diupload Dosen
                    val materiUrl = snapshot.child("fileUrl").value?.toString()
                    val fileName = snapshot.child("fileName").value?.toString() ?: "Lampiran_Materi.pdf"

                    if (!materiUrl.isNullOrEmpty() && materiUrl != "null") {
                        btnDownloadMateri.visibility = View.VISIBLE
                        btnDownloadMateri.text = "📁 Unduh Lampiran Tugas"
                        btnDownloadMateri.setOnClickListener { downloadFile(materiUrl, fileName) }
                    } else {
                        btnDownloadMateri.visibility = View.GONE
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        setupCommentsSystem()

        val currentUserId = auth.currentUser?.uid
        if (currentUserId != null && currentUserId == creatorId) {
            role = "DOSEN"
            setupDosenUI()
        } else {
            role = "MAHASISWA"
            setupMahasiswaUI()
        }
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

    private fun uploadTask(answer: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBarTask)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitTask)

        if (selectedFileUri != null) {
            progressBar.visibility = View.VISIBLE
            btnSubmit.isEnabled = false

            MediaManager.get().upload(selectedFileUri)
                .option("folder", "submissions/$taskId/")
                .option("upload_preset", "tugas_preset")
                .callback(object : UploadCallback {
                    override fun onStart(requestId: String) {}
                    override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                    override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                        val fileUrl = resultData["secure_url"].toString()
                        saveSubmissionToDatabase(answer, fileUrl)
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            btnSubmit.isEnabled = true
                        }
                    }

                    override fun onError(requestId: String, error: ErrorInfo) {
                        runOnUiThread {
                            progressBar.visibility = View.GONE
                            btnSubmit.isEnabled = true
                            Toast.makeText(this@TaskActivity, "Gagal Upload: ${error.description}", Toast.LENGTH_LONG).show()
                        }
                    }

                    override fun onReschedule(requestId: String, error: ErrorInfo) {}
                })
                .dispatch()
        } else {
            saveSubmissionToDatabase(answer, null)
        }
    }

    private fun saveSubmissionToDatabase(answer: String, fileUrl: String?) {
        val currentUid = auth.currentUser?.uid ?: return
        val data = mutableMapOf<String, Any?>(
            "userId" to currentUid,
            "userName" to (auth.currentUser?.displayName ?: "Mahasiswa"),
            "answer" to answer,
            "grade" to null,
            "timestamp" to ServerValue.TIMESTAMP
        )
        if (fileUrl != null) data["fileUrl"] = fileUrl

        dbRef.child("submissions").child(taskId).child(currentUid).setValue(data)
            .addOnSuccessListener {
                Toast.makeText(this, "Tugas Berhasil Terkirim!", Toast.LENGTH_SHORT).show()
                selectedFileUri = null
                findViewById<TextView>(R.id.tvFileName).text = "Belum ada file dipilih"
            }
    }

    private fun setupCommentsSystem() {
        val rvComments = findViewById<RecyclerView>(R.id.rvComments)
        val etComment = findViewById<EditText>(R.id.etCommentInput)
        val btnSend = findViewById<ImageButton>(R.id.btnSendComment)

        commentAdapter = CommentAdapter(commentList)
        rvComments.layoutManager = LinearLayoutManager(this)
        rvComments.adapter = commentAdapter

        dbRef.child("comments").child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                commentList.clear()
                for (data in snapshot.children) {
                    val comment = data.getValue(Comment::class.java)
                    comment?.let { commentList.add(it) }
                }
                commentAdapter.notifyDataSetChanged()
                if (commentList.isNotEmpty()) rvComments.scrollToPosition(commentList.size - 1)
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        btnSend.setOnClickListener {
            val msg = etComment.text.toString().trim()
            if (msg.isNotEmpty()) {
                val commentId = dbRef.child("comments").child(taskId).push().key ?: return@setOnClickListener
                val newComment = Comment(
                    commentId = commentId,
                    userId = auth.currentUser?.uid ?: "",
                    userName = auth.currentUser?.displayName ?: "User",
                    message = msg,
                    timestamp = System.currentTimeMillis()
                )
                dbRef.child("comments").child(taskId).child(commentId).setValue(newComment)
                etComment.text.clear()
            }
        }
    }

    private fun setupDosenUI() {
        findViewById<LinearLayout>(R.id.layoutDosen).visibility = View.VISIBLE
        val rvSubmissions = findViewById<RecyclerView>(R.id.rvSubmissions)

        submissionAdapter = SubmissionAdapter(submissionList) { studentData ->
            showGradeDialog(
                studentData["userId"].toString(),
                studentData["userName"].toString(),
                studentData["answer"].toString(),
                studentData["fileUrl"]?.toString()
            )
        }
        rvSubmissions.layoutManager = LinearLayoutManager(this)
        rvSubmissions.adapter = submissionAdapter

        dbRef.child("submissions").child(taskId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                submissionList.clear()
                for (data in snapshot.children) {
                    val item = data.value as? Map<String, Any>
                    item?.let { submissionList.add(it) }
                }
                submissionAdapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showGradeDialog(uid: String, name: String, ans: String, url: String?) {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 40, 60, 40)
        }
        layout.addView(TextView(this).apply {
            text = "Jawaban:\n$ans"
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, 20)
        })

        if (!url.isNullOrEmpty() && url != "null") {
            layout.addView(Button(this).apply {
                text = "Lihat Lampiran Mahasiswa"
                setBackgroundColor(Color.parseColor("#1A73E8"))
                setTextColor(Color.WHITE)
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            })
        }

        val etGrade = EditText(this).apply {
            hint = "Masukkan Nilai"; inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(etGrade)

        AlertDialog.Builder(this)
            .setTitle("Nilai: $name")
            .setView(layout)
            .setPositiveButton("Simpan") { _, _ ->
                val g = etGrade.text.toString()
                if (g.isNotEmpty()) dbRef.child("submissions").child(taskId).child(uid).child("grade").setValue(g)
            }.setNegativeButton("Batal", null).show()
    }

    private fun downloadFile(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Mengunduh lampiran tugas...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Mengunduh...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    private fun checkDeadlineLogic(deadlineStr: String) {
        if (deadlineStr == "Tidak ada batas waktu" || deadlineStr.isEmpty()) return
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val deadlineDate = sdf.parse(deadlineStr)
            if (deadlineDate != null && Date().after(deadlineDate)) {
                findViewById<Button>(R.id.btnSubmitTask)?.apply {
                    isEnabled = false
                    text = "Waktu Habis"
                    setBackgroundColor(Color.GRAY)
                }
            }
        } catch (e: Exception) {}
    }

    private fun setupMahasiswaUI() {
        findViewById<LinearLayout>(R.id.layoutMahasiswa).visibility = View.VISIBLE
        val etSubmission = findViewById<EditText>(R.id.etSubmissionText)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitTask)
        val btnAttach = findViewById<Button>(R.id.btnUploadFile)
        val tvStatus = findViewById<TextView>(R.id.tvGradeStatus)

        val currentUid = auth.currentUser?.uid ?: return

        dbRef.child("submissions").child(taskId).child(currentUid)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val grade = snapshot.child("grade").value
                        tvStatus.text = if (grade != null) "Nilai Anda: $grade" else "Status: Sudah Mengumpulkan"
                        btnSubmit.text = "Perbarui Tugas"
                    } else {
                        tvStatus.text = "Status: Belum Mengumpulkan"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        btnAttach.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            startActivityForResult(Intent.createChooser(intent, "Pilih file"), PICK_FILE_REQUEST)
        }

        btnSubmit.setOnClickListener {
            val answer = etSubmission.text.toString().trim()
            if (answer.isEmpty() && selectedFileUri == null) {
                Toast.makeText(this, "Isi jawaban atau lampirkan file!", Toast.LENGTH_SHORT).show()
            } else {
                uploadTask(answer)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedFileUri = data?.data
            findViewById<TextView>(R.id.tvFileName).text = "File siap dikirim"
        }
    }
}