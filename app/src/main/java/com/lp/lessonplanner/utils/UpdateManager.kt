package com.lp.lessonplanner.utils

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.lp.lessonplanner.data.remote.UpdateResponse
import java.io.File

class UpdateManager(private val context: Context) {

    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var downloadId: Long = -1

    fun downloadAndInstall(update: UpdateResponse) {
        val uri = Uri.parse(update.downloadUrl)
        val request = DownloadManager.Request(uri)
            .setTitle("Downloading Lesson Planner Gh Update")
            .setDescription("Version ${update.versionName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "lesson_planner_update.apk")
            .setMimeType("application/vnd.android.package-archive")

        downloadId = downloadManager.enqueue(request)
        Toast.makeText(context, "Download started...", Toast.LENGTH_SHORT).show()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk()
                    context?.unregisterReceiver(this)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    @SuppressLint("Range")
    private fun installApk() {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor != null && cursor.moveToFirst()) {
            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                val localUriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                val apkUri = Uri.parse(localUriString)
                
                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                if (apkUri.scheme == "content") {
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive")
                } else {
                    // Try to convert file:// to content:// via FileProvider if needed
                    val path = apkUri.path
                    if (path != null) {
                        val fileToInstall = File(path)
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            fileToInstall
                        )
                        installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive")
                    }
                }

                try {
                    context.startActivity(installIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Error opening installer: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            cursor.close()
        }
    }
}
