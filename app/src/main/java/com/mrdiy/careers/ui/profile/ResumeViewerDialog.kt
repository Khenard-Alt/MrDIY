package com.mrdiy.careers.ui.profile

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mrdiy.careers.data.repository.ResumeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Full-screen in-app resume viewer, so no external app is needed. Downloads the file from a signed
 * URL and shows it by type: PDF pages as images (PdfRenderer), Word (.docx) and text files as text.
 */
class ResumeViewerDialog : DialogFragment() {

    private var renderer: PdfRenderer? = null
    private var descriptor: ParcelFileDescriptor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        val root = FrameLayout(ctx).apply { setBackgroundColor(Color.parseColor("#EEEEEE")) }

        val progress = ProgressBar(ctx)
        val message = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setPadding((24 * density).toInt(), 0, (24 * density).toInt(), 0)
            visibility = View.GONE
        }
        val close = TextView(ctx).apply {
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#99000000"))
            setPadding(40, 20, 40, 20)
            setOnClickListener { dismiss() }
        }

        root.addView(progress, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(message, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        root.addView(close, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            setMargins(0, (40 * density).toInt(), (12 * density).toInt(), 0)
        })

        val url = requireArguments().getString(ARG_URL).orEmpty()
        viewLifecycleOwnerLiveData.observe(this) { owner ->
            owner ?: return@observe
            owner.lifecycleScope.launch {
                try {
                    val content = withContext(Dispatchers.IO) { load(url) }
                    val view = when (content) {
                        is Content.Pdf -> pdfView(content.renderer)
                        is Content.Text -> textView(content.text)
                    }
                    root.addView(view, 0, FrameLayout.LayoutParams(-1, -1))
                } catch (e: Exception) {
                    message.text = "Could not open resume. Please try again."
                    message.visibility = View.VISIBLE
                } finally {
                    progress.visibility = View.GONE
                }
            }
        }
        return root
    }

    private sealed interface Content {
        class Pdf(val renderer: PdfRenderer) : Content
        class Text(val text: String) : Content
    }

    private fun load(url: String): Content {
        val bytes = URL(url).openStream().use(ResumeFiles::readBounded)
        val isPdf = bytes.size >= 5 && String(bytes, 0, 5, Charsets.US_ASCII) == "%PDF-"
        val isZip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        return when {
            isPdf -> {
                val file = File(requireContext().cacheDir, "resume_view.pdf").apply { writeBytes(bytes) }
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                descriptor = fd
                Content.Pdf(PdfRenderer(fd).also { renderer = it })
            }
            isZip -> Content.Text(docxText(bytes))
            else -> Content.Text(String(bytes, Charsets.UTF_8))
        }
    }

    /** Plain text of a .docx (formatting and images are not shown), one paragraph per line. */
    private fun docxText(bytes: ByteArray): String {
        java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "word/document.xml" }
                ?: error("Not a Word document")
            return zip.readBytes().toString(Charsets.UTF_8)
                .replace("</w:p>", "\n")
                .replace("<w:tab/>", "\t")
                .replace(Regex("<[^>]+>"), "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&apos;", "'")
                .lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim()
        }
    }

    private fun textView(text: String): View {
        val ctx = requireContext()
        val pad = (20 * resources.displayMetrics.density).toInt()
        val body = TextView(ctx).apply {
            this.text = text.ifBlank { "This document has no readable text." }
            textSize = 14f
            setTextColor(Color.parseColor("#222222"))
            setLineSpacing(0f, 1.2f)
            setPadding(pad, pad * 3, pad, pad)
            setTextIsSelectable(true)
        }
        return ScrollView(ctx).apply {
            setBackgroundColor(Color.WHITE)
            addView(body, ViewGroup.LayoutParams(-1, -2))
        }
    }

    private fun pdfView(pdf: PdfRenderer): View {
        val ctx = requireContext()
        return RecyclerView(ctx).apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = PageAdapter(pdf, resources.displayMetrics.widthPixels)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        renderer?.close()
        descriptor?.close()
        renderer = null
        descriptor = null
    }

    private class PageAdapter(
        private val pdf: PdfRenderer, private val width: Int
    ) : RecyclerView.Adapter<PageAdapter.VH>() {
        class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun getItemCount() = pdf.pageCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val img = ImageView(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2).apply { bottomMargin = 12 }
                adjustViewBounds = true
                setBackgroundColor(Color.WHITE)
            }
            return VH(img)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            // PdfRenderer allows only one open page at a time; bind runs on the main thread.
            pdf.openPage(position).use { page ->
                val height = width * page.height / page.width
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                holder.image.setImageBitmap(bmp)
            }
        }
    }

    companion object {
        private const val ARG_URL = "url"

        fun show(fm: FragmentManager, url: String) {
            ResumeViewerDialog().apply {
                arguments = Bundle().apply { putString(ARG_URL, url) }
            }.show(fm, "resume_viewer")
        }
    }
}
