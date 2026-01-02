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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
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

        // 1. Setup UI Dasar
        val toolbar = findViewById<Toolbar>(R.id.toolbarTask)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Detail Tugas"
        toolbar.setNavigationOnClickListener { onBackPressed() }

        taskId = intent.getStringExtra("TASK_ID") ?: ""
        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        val taskTitle = intent.getStringExtra("TASK_TITLE") ?: "Detail Tugas"
        val taskDesc = intent.getStringExtra("TASK_DESC") ?: "Tidak ada deskripsi"
        val creatorId = intent.getStringExtra("CREATOR_ID")

        findViewById<TextView>(R.id.tvTaskTitleDetail).text = taskTitle
        findViewById<TextView>(R.id.tvTaskDescriptionDetail).text = taskDesc

        // 2. Ambil Data Tugas (Deadline & File Materi dari Dosen)
        val tvDeadline = findViewById<TextView>(R.id.tvTaskDeadline)
        val btnDownloadMateri = findViewById<Button>(R.id.btnDownloadMateri) // Pastikan ID ini ada di layout

        dbRef.child("tasks").child(taskId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Handle Deadline
                val deadline = snapshot.child("deadline").value?.toString() ?: "Tidak ada batas waktu"
                tvDeadline.text = "Batas Waktu: $deadline"
                checkDeadlineLogic(deadline)

                // --- FITUR BARU: Download Materi dari Dosen ---
                val materiUrl = snapshot.child("fileUrl").value?.toString()
                val fileName = snapshot.child("fileName").value?.toString() ?: "Materi_Tugas.pdf"

                if (!materiUrl.isNullOrEmpty()) {
                    btnDownloadMateri.visibility = View.VISIBLE
                    btnDownloadMateri.setOnClickListener {
                        // Opsi 1: Buka di Browser
                        // startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(materiUrl)))

                        // Opsi 2: Download Langsung ke HP
                        downloadFile(materiUrl, fileName)
                    }
                } else {
                    btnDownloadMateri.visibility = View.GONE
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

    // --- LOGIKA DOWNLOAD FILE ---
    private fun downloadFile(url: String, fileName: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Mengunduh materi tugas...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            Toast.makeText(this, "Unduhan dimulai. Cek panel notifikasi.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal mengunduh: ${e.message}", Toast.LENGTH_SHORT).show()
            // Fallback buka browser jika DownloadManager gagal
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }

    // --- FITUR KOMENTAR KELAS (Tetap Sama) ---
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

    private fun checkDeadlineLogic(deadlineStr: String) {
        if (deadlineStr == "Tidak ada batas waktu") return
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val deadlineDate = sdf.parse(deadlineStr)
            if (deadlineDate != null && Date().after(deadlineDate)) {
                findViewById<Button>(R.id.btnSubmitTask)?.apply {
                    isEnabled = false
                    text = "Waktu Habis"
                    setBackgroundColor(Color.GRAY)
                }
                findViewById<TextView>(R.id.tvTaskDeadline).setTextColor(Color.RED)
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    // --- UI MAHASISWA ---
    private fun setupMahasiswaUI() {
        val layoutMahasiswa = findViewById<LinearLayout>(R.id.layoutMahasiswa)
        val etSubmission = findViewById<EditText>(R.id.etSubmissionText)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitTask)
        val btnAttach = findViewById<Button>(R.id.btnUploadFile)
        val tvStatus = findViewById<TextView>(R.id.tvGradeStatus)
        val tvFileName = findViewById<TextView>(R.id.tvFileName)

        layoutMahasiswa?.visibility = View.VISIBLE
        val currentUid = auth.currentUser?.uid ?: return

        dbRef.child("submissions").child(taskId).child(currentUid)
            .addValueEventListener(object : ValueEventListener {
                @SuppressLint("SetTextI18n")
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val text = snapshot.child("answer").value?.toString() ?: ""
                        if (etSubmission.text.isEmpty()) etSubmission.setText(text)
                        val grade = snapshot.child("grade").value
                        var info = "Tugas selesai dikumpulkan"
                        if (grade != null && grade.toString() != "null" && grade.toString().isNotEmpty()) {
                            info += "\nTugas dinilai = $grade"
                            tvStatus.setTextColor(Color.parseColor("#0F9D58"))
                        } else {
                            tvStatus.setTextColor(Color.BLUE)
                        }
                        tvStatus.text = info
                        btnSubmit.text = "Update Tugas"
                    } else {
                        tvStatus.text = "Status: Belum Mengumpulkan"
                        tvStatus.setTextColor(Color.BLACK)
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        btnAttach?.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "*/*" }
            startActivityForResult(Intent.createChooser(intent, "Pilih file jawaban"), PICK_FILE_REQUEST)
        }

        btnSubmit.setOnClickListener {
            val answer = etSubmission.text.toString().trim()
            if (answer.isNotEmpty() || selectedFileUri != null) {
                uploadTask(answer)
            } else {
                Toast.makeText(this, "Isi jawaban atau pilih file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uploadTask(answer: String) {
        val currentUid = auth.currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().getReference("submissions/$taskId/$currentUid")

        if (selectedFileUri != null) {
            Toast.makeText(this, "Sedang mengirim tugas...", Toast.LENGTH_SHORT).show()
            storageRef.putFile(selectedFileUri!!).addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    saveSubmissionToDatabase(answer, uri.toString())
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Gagal upload file: ${it.message}", Toast.LENGTH_SHORT).show()
            }
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
                Toast.makeText(this, "Tugas Berhasil Dikirim!", Toast.LENGTH_SHORT).show()
                selectedFileUri = null // Reset pilihan file setelah sukses
            }
    }

    // --- UI DOSEN ---
    private fun setupDosenUI() {
        val layoutDosen = findViewById<LinearLayout>(R.id.layoutDosen)
        val rvSubmissions = findViewById<RecyclerView>(R.id.rvSubmissions)
        layoutDosen?.visibility = View.VISIBLE

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
            text = "Jawaban Teks:\n$ans"
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
            hint = "Input Nilai (0-100)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(etGrade)

        AlertDialog.Builder(this)
            .setTitle("Penilaian: $name")
            .setView(layout)
            .setPositiveButton("Simpan Nilai") { _, _ ->
                val g = etGrade.text.toString()
                if (g.isNotEmpty()) {
                    dbRef.child("submissions").child(taskId).child(uid).child("grade").setValue(g)
                    Toast.makeText(this, "Nilai disimpan", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            selectedFileUri = data?.data
            findViewById<TextView>(R.id.tvFileName).text = "File siap: ${selectedFileUri?.lastPathSegment}"
        }
    }
}