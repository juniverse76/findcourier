package xyz.juniverse.findcourier

import android.Manifest
import android.content.Context
import android.graphics.Color
import android.os.*
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import okhttp3.*
import xyz.juniverse.findcourier.databinding.ActivityMainBinding
import java.io.IOException
import java.lang.reflect.Type
import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private val reqCode = 1111

    private val cj_api_endpoint = "http://nplus.doortodoor.co.kr/web/detail.jsp?slipno="
    private val sample_invoice = "655095983211"
    private val dbCollection = "AddressToCourier"
    private val dbDocument = "List"

    private lateinit var binding: ActivityMainBinding

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var preview: Preview
    private lateinit var qrAnalysis: ImageAnalysis
    private var cameraStandBy = true
    private val permissions = listOf(
        Manifest.permission.CAMERA
    )

    private val vibrator by lazy { getVibrator() }
    private val quickFeedbackEffect =
        VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE)

    private val okClient by lazy { OkHttpClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

//        binding.invoice.setText(sample_invoice)     // test
        binding.searchInvoice.setOnClickListener {
            it.isEnabled = false

            binding.courierName.text = ""
//            binding.courierNumber.text = ""
            binding.foundInfo.visibility = View.GONE
            binding.loading.visibility = View.VISIBLE

            val invoiceText = binding.invoice.text
            val request = Request.Builder().url("$cj_api_endpoint$invoiceText").build()
            okClient.newCall(request).enqueue(httpCallback)
        }

//        binding.address.setText("someAddress")      // test
        binding.searchAddress.setOnClickListener {
            // 키보드 닫기.
            currentFocus?.let { view ->
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }

            binding.courierName.text = ""
//            binding.courierNumber.text = ""
            binding.foundInfo.visibility = View.GONE
            binding.loading.visibility = View.VISIBLE
            val address = binding.address.text.toString()
            Firebase.firestore.collection(dbCollection).document(dbDocument).get()
                .addOnSuccessListener { doc ->
                    val name = doc.get(address)
                    Log.i("juniverse", "name?? $name")
                    runOnUiThread {
                        name?.let {
                            it as String
                            binding.foundInfo.visibility = View.VISIBLE
                            binding.notFoundInfo.visibility = View.GONE
                            binding.courierName.text = it
                        } ?: run {
                            binding.foundInfo.visibility = View.GONE
                            binding.notFoundInfo.visibility = View.VISIBLE
                            // 입력 창을 보여줘서 저장할 수 있게
                            binding.newName.setText("")
                        }
                        binding.loading.visibility = View.GONE
                    }
                }
        }

        binding.addNew.setOnClickListener {
            // 주소와 이름 추가.
            binding.address.text
            binding.newName.text
            val newData = hashMapOf(
                binding.address.text.toString() to binding.newName.text.toString()
            )
            Firebase.firestore.collection(dbCollection).document(dbDocument).set(newData, SetOptions.merge())
                .addOnSuccessListener {
                    Snackbar.make(binding.background, R.string.name_added, Snackbar.LENGTH_LONG).apply {
                        setAction(R.string.cancel) {
                            // 취소
                            val ref = Firebase.firestore.collection(dbCollection).document(dbDocument)
                            val updates = hashMapOf<String, Any>(
                                binding.address.text.toString() to FieldValue.delete()
                            )
                            ref.update(updates)
                            runOnUiThread {
                                Snackbar.make(binding.background, R.string.cancel_complete, Snackbar.LENGTH_SHORT).show()
                            }
                        }
                        setActionTextColor(Color.RED)
                        show()
                    }
                }
                .addOnFailureListener {
                    Log.e("juniverse", "error?? $it")
                }
        }

        binding.loading.visibility = View.GONE
        binding.foundInfo.visibility = View.GONE
        binding.notFoundInfo.visibility = View.GONE

        // 카메라 세팅해서 스캐너 역할도 할 수 있게.
        checkPermissions(permissions, reqCode)
    }

    private val httpCallback = object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.w("juniverse", "shit, failed... $e")
            runOnUiThread {
                binding.searchInvoice.isEnabled = true
            }
        }

        override fun onResponse(call: Call, response: Response) {
//            Log.i("juniverse", "response : ${response.body?.string()}")
            response.body?.let {
                val result = it.string()
                var startIndex = result.indexOf("배송")
                if (startIndex >= 0) {
                    var subStr = result.substring(startIndex, startIndex + 200)
                    subStr = subStr.replace("&nbsp;", "")
                    startIndex = 0
                    for (i in 0..2) {
                        startIndex = subStr.indexOf("<td>", startIndex+1)
                    }
                    var lastIndex = subStr.indexOf("</td>", startIndex)
                    val name = subStr.substring(startIndex+4, lastIndex)
                    Log.i("juniverse", "name? '$name'")
                    startIndex = subStr.indexOf("<td>", startIndex+1)
                    lastIndex = subStr.indexOf("</td>", startIndex)
                    val number = subStr.substring(startIndex+4, lastIndex)
                    Log.i("juniverse", "number? '$number'")

                    runOnUiThread {
                        binding.foundInfo.visibility = View.VISIBLE
                        binding.notFoundInfo.visibility = View.GONE
                        binding.loading.visibility = View.GONE
                        binding.courierName.text = name
//                        binding.courierNumber.text = number
                    }
                }
            }
            runOnUiThread {
                binding.searchInvoice.isEnabled = true
            }
        }
    }

    override fun onRequestPermissionsResult(
        reqCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(reqCode, permissions, grantResults)
        if (reqCode == this.reqCode && allPermissionsGranted(this.permissions))
            startCamera()
        else
            finish()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted(permissions) && cameraStandBy)
            startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    private fun startCamera() {
        // TODO
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        Log.d("juniverse", "start camera")
        cameraProviderFuture.addListener({
            // cameraX에서 사용하는 lifecycle 관리용
            cameraProvider = cameraProviderFuture.get()

            // 프리뷰 보여주기용 lifecycle
            preview = Preview.Builder()
                .setTargetResolution(Size(binding.scanner.width, binding.scanner.height))
                .build()
                .also {
                    it.setSurfaceProvider(binding.scanner.surfaceProvider)
                }

            // QR 코드 읽기용 lifecycle
            qrAnalysis = getQRAnalyzer()

            // 최초 사용할 카메라 (back camera)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 먼저 모든 lifecycle 해제.
                cameraProvider.unbindAll()

                // 사용할 lifecycle 연결
                cameraProvider.bindToLifecycle(
                    this, cameraSelector,
                    preview,
//                    imageCapture,
                    qrAnalysis
                )

                cameraStandBy = true
            } catch (exc: Exception) {
                Log.e("juniverse", "Use case binding failed $exc")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun getQRAnalyzer(): ImageAnalysis {
        return ImageAnalysis.Builder()
//            .setTargetResolution(Size(viewFinder.width, viewFinder.height))
//            .setTargetRotation(Surface.ROTATION_90)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, QrCodeAnalyzer { result ->
                    Log.d("juniverse", "checking!!!!")
                    if (!cameraStandBy) return@QrCodeAnalyzer

                    // 전에 스캔한 적이 있는지 확인해보자.
                    val invoiceText = result.text
//                    val qrHash = getScanHashStatus(result.text) ?: return@QrCodeAnalyzer

                    Log.d("juniverse", "qr text: ${result.text}, points: ${result.resultPoints}")
                    cameraStandBy = false
                    // then send post delayed to resume camera
                    Handler(Looper.getMainLooper()).postDelayed({
                        startCamera()
                    }, 2000)

                    vibrator.vibrate(quickFeedbackEffect)

                    // API 호출.
                    val request = Request.Builder().url("$cj_api_endpoint$invoiceText").build()
                    okClient.newCall(request).enqueue(httpCallback)

                    runOnUiThread {
                        cameraProvider.unbind(preview)
                        cameraProvider.unbind(qrAnalysis)
                        binding.invoice.setText(invoiceText)
                    }
                })
            }
    }

}