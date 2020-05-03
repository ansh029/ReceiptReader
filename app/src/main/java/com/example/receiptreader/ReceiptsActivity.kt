
package com.example.receiptreader

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Camera
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.receiptreader.Keys.API_KEY_FIREBASE
import com.example.receiptreader.Keys.APP_ID
import com.example.receiptreader.Keys.APP_NAME
import com.example.receiptreader.R
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.text.FirebaseVisionTextRecognizer
import com.squareup.picasso.Picasso

import kotlinx.android.synthetic.main.activity_receipts_home.*
import java.io.File
import java.io.FileNotFoundException
import java.text.SimpleDateFormat
import java.util.*


class ReceiptsActivity : AppCompatActivity() {
    private val UPLOAD_ACTION = 2001
    private val PERMISSION_ACTION = 2002
    private val CAMERA_ACTION = 2003
    private lateinit var photoImage: Bitmap
    private lateinit var firebaseImage: FirebaseVisionImage
    private lateinit var textDeviceDetector: FirebaseVisionTextRecognizer
    lateinit var imageURI: Uri

    lateinit var matrix: Matrix


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_receipts_home)

        val firebaseOptions = FirebaseOptions.Builder()
            .setApiKey(API_KEY_FIREBASE)
            .setApplicationId(APP_ID).build()
        FirebaseApp.initializeApp(this, firebaseOptions, APP_NAME)
        textDeviceDetector = FirebaseVision.getInstance().getOnDeviceTextRecognizer()
        //check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_ACTION)
        }
        setUI()
    }

    private fun setUI() {
        txtUpload.setOnClickListener {
            startActivityForResult(uploadIntent(), UPLOAD_ACTION)
        }

        txtCamera.setOnClickListener {
            startActivityForResult(cameraIntent(this), CAMERA_ACTION)
        }
    }

    private fun uploadAction(data: Intent) {
        try {
            val stream = contentResolver.openInputStream(data.getData()!!)
            if (::photoImage.isInitialized) photoImage.recycle()

            photoImage = BitmapFactory.decodeStream(stream)


            firebaseImage = FirebaseVisionImage.fromBitmap(photoImage)
            imageResult.setImageBitmap(photoImage)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun cameraAction() {
        try {

            Picasso.with(this).load(imageURI).into(imageResult)
            firebaseImage = FirebaseVisionImage.fromFilePath(this, imageURI)
        } catch (e: FileNotFoundException) {
            e.printStackTrace()
        }
    }



    private fun textRecognitionAction() {
        var text = ""
       textDeviceDetector.processImage(firebaseImage)
                .addOnSuccessListener {
                    for (block in it.textBlocks) text += block.text + "\n"
                    val receipts = getReceipts(text)
                    editTotal.setText(receipts.total, TextView.BufferType.EDITABLE)
                    editLocation.setText(receipts.type, TextView.BufferType.EDITABLE)
                    editVAT.setText(receipts.vat, TextView.BufferType.EDITABLE)
                }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                UPLOAD_ACTION -> uploadAction(data!!)
                CAMERA_ACTION -> cameraAction()
            }
            textRecognitionAction()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        if (requestCode == PERMISSION_ACTION)
            if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) imageResult.setEnabled(true)
    }


    fun uploadIntent(): Intent {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        return intent
    }

    fun cameraIntent(context: Context): Intent {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "IMG_" + timeStamp + "_"
        val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val filephoto = File.createTempFile(
            imageFileName, /* prefix */
            ".jpg", /* suffix */
            storageDir      /* directory */
        )
        imageURI = FileProvider.getUriForFile(context,  "com.example.receiptreader.provider", filephoto)
        val pictureIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        pictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageURI)
        return pictureIntent
    }

    fun getReceipts(text: String): Receipts {
        val originalResult = text.findFloat()
        if (originalResult.isEmpty()) return Receipts()
        else {
            val receipts = Receipts()
            val totalF = Collections.max(originalResult)
            val secondLargestF = findSecondLargestFloat(originalResult)
            receipts.total = totalF.toString()
            receipts.vat = if (secondLargestF == 0.0f) "0" else "%.2f".format(totalF - secondLargestF)
            receipts.type = text.firstLine()
            return receipts
        }
    }

    private fun findSecondLargestFloat(input: ArrayList<Float>?): Float {
        if (input == null || input.isEmpty() || input.size == 1) return 0.0f
        else {
            try {
                val tempSet = HashSet(input)
                val sortedSet = TreeSet(tempSet)
                return sortedSet.elementAt(sortedSet.size - 2)
            } catch (e: Exception) {
                return 0.0f
            }
        }
    }

}
