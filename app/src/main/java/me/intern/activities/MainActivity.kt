package me.intern.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import me.intern.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //val url = "http://sv4avadl.uploadt.com/Serial/Friends/S05/Friends%20S05E20%20BrRip%201080p%20x265_Serial.AVADL.NeT.m4v"
        //val url = "https://pagalworld3.net/14331/Dilbar%20-%20Satyameva%20Jayate%20(HD%201080p).mp4"
        //val url = "http://home.iitj.ac.in/~suthar.2/300.mkv"
        //http://sv4avadl.uploadt.com/Serial/Friends/S05/Friends%20S05E20%20BrRip%201080p%20x265_Serial.AVADL.NeT.m4v
        //https://pagalworld3.net/14331/Dilbar%20-%20Satyameva%20Jayate%20(HD%201080p).mp4


        btn_start.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                val url = "https://socialcops.com/images/old/spec/home/header-img-background_video-1920-480.mp4"
                val intent = Intent(this, PlayerActivity::class.java)
                intent.putExtra("videoUrl", url)
                startActivity(intent)
            } else {
                Toast.makeText(this, "Please provide storage permission first!", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 75)
            }
        }
    }
}
