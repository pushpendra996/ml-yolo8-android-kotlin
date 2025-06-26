package com.pushpendra.pocsphere

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class GalleryActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        val recyclerView = findViewById<RecyclerView>(R.id.galleryRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        val images = getGalleryImages()
        val adapter = GalleryAdapter(images) { imagePath ->
            val intent = Intent(this, ImagePreviewActivity::class.java)
            intent.putExtra("imagePath", imagePath)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
    }

    private fun getGalleryImages(): List<String> {
        val dir = File(ImageCaptureUtil(this).getCaptureDirectoryPath())
        val files = dir.listFiles()?.filter { it.isFile && it.name.endsWith(".jpg") }?.map { it.absolutePath } ?: emptyList()
        android.util.Log.d("GalleryActivity", "Gallery directory: ${dir.absolutePath}, files: ${files.size}")
        return files
    }
}

class GalleryAdapter(
    private val images: List<String>,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {
    class ViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val imageView = ImageView(parent.context)
        val size = parent.resources.displayMetrics.widthPixels / 3
        imageView.layoutParams = android.view.ViewGroup.LayoutParams(size, size)
        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val bitmap = BitmapFactory.decodeFile(images[position])
        holder.imageView.setImageBitmap(bitmap)
        holder.imageView.setOnClickListener { onClick(images[position]) }
    }

    override fun getItemCount() = images.size
} 