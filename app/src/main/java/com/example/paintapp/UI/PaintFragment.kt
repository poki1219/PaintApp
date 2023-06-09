package com.example.paintapp.UI

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.graphics.*
import android.os.Bundle
import android.text.Editable
import android.util.Base64
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.paintapp.*
import com.example.paintapp.data.CustomPath
import com.example.paintapp.data.PaintCanvas
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL


class PaintFragment : Fragment(), CustomEventListener {
    private lateinit var mainActivity: MainActivity
    private lateinit var paintView: PaintView
    private lateinit var viewModel: PaintViewModel
    private lateinit var paintViewContainer: FrameLayout
    private lateinit var paintFragmentView: View
    private lateinit var answerBtn: ImageButton
    private var isStrokeSelected = false
    private var strokePosition: PointF = PointF(0F, 0F)
    private var question = ""

    private val observer = Observer<String> { m ->
        val modelAnswer = ModelAnswer(mainActivity)
        modelAnswer.visibility = LinearLayout.VISIBLE

        val layoutParams = LinearLayout.LayoutParams(800, 500)
        layoutParams.leftMargin = strokePosition.x.toInt()
        layoutParams.topMargin = strokePosition.y.toInt()

        modelAnswer.layoutParams = layoutParams

        val closeBtn = modelAnswer.findViewById<ImageView>(R.id.close)
        val minimizeBtn = modelAnswer.findViewById<ImageView>(R.id.minimize)
        val questionBarTextView = modelAnswer.findViewById<TextView>(R.id.question_bar)
        val questionTextView = modelAnswer.findViewById<EditText>(R.id.question)
        val answerTextView = modelAnswer.findViewById<TextView>(R.id.answer)
        val gptReRequest = modelAnswer.findViewById<ImageButton>(R.id.model_answer_gpt_request)
        val modelAnswerTopBar = modelAnswer.findViewById<LinearLayout>(R.id.model_answer_top_bar)
        val scrollView = modelAnswer.findViewById<ScrollView>(R.id.answer_field)

        if (question == "") {
            mainActivity.runOnUiThread {
                Toast.makeText(mainActivity, "텍스트 인식에 실패했습니다.", Toast.LENGTH_LONG).show()
            }
        }

        questionTextView.text = Editable.Factory.getInstance().newEditable(question)
        questionBarTextView.text = context?.getString(R.string.app_gpt_full_question, question)
        answerTextView.text = context?.getString(R.string.app_gpt_answer, m)

        closeBtn.setOnClickListener {
            paintViewContainer.removeView(modelAnswer)
        }

        minimizeBtn.setOnClickListener {
            if (scrollView.visibility == VISIBLE)
                scrollView.visibility = GONE
            else if(scrollView.visibility == GONE)
                scrollView.visibility = VISIBLE
        }

        gptReRequest.setOnClickListener {
            (mainActivity.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?)?.hideSoftInputFromWindow(
                questionTextView.windowToken,
                0
            );
            questionBarTextView.text =
                context?.getString(R.string.app_gpt_full_question, questionTextView.text.toString())
            gptRequest(questionTextView.text.toString(), answerTextView)
        }

        var moveX = strokePosition.x
        var moveY = strokePosition.y
        modelAnswerTopBar.setOnTouchListener { _, event->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> {
                    moveX = modelAnswer.x - event.rawX
                    moveY = modelAnswer.y - event.rawY
                }
                MotionEvent.ACTION_MOVE -> {
                    modelAnswer.animate()
                        .x(event.rawX + moveX)
                        .y(event.rawY + moveY)
                        .setDuration(0)
                        .start()
                }
            }
            return@setOnTouchListener true
        }
        paintViewContainer.addView(modelAnswer)
        modelAnswer.bringToFront()
    }
    companion object {
        fun newInstance() = PaintFragment()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        mainActivity = context as MainActivity
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_paint, container, false)
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        paintFragmentView = view

        viewModel = ViewModelProvider(this)[PaintViewModel::class.java]
        viewModel.pathList.observe(viewLifecycleOwner, Observer<ArrayList<CustomPath>> { paths ->
            paintView.pathList = paths
        })

        viewModel.addFile(PaintCanvas("Blank"))

        val selectBrush = mainActivity.findViewById<ImageView>(R.id.selectBrush)
        val brushBtnGroup = mainActivity.findViewById<LinearLayout>(R.id.brushGroup)
        val redBtn = mainActivity.findViewById<ImageButton>(R.id.redColor)
        val blueBtn = mainActivity.findViewById<ImageButton>(R.id.blueColor)
        val blackBtn = mainActivity.findViewById<ImageButton>(R.id.blackColor)
        val clearBtn = mainActivity.findViewById<ImageView>(R.id.clear)
        answerBtn = mainActivity.findViewById<ImageButton>(R.id.answerBtn)
        paintViewContainer = mainActivity.findViewById(R.id.paint_view_container)

        selectBrush.setOnClickListener {
            paintView.displaySelectBox = false
            paintView.isSelect = false
            paintView.selectedStroke.clear()
            if (paintView.selectMode) {
                paintView.selectMode = false
                brushBtnGroup.visibility = View.VISIBLE
            }
            else {
                paintView.selectMode = true
                brushBtnGroup.visibility = View.GONE
            }
        }
        redBtn.setOnClickListener {
            if (!paintView.selectMode) {
                paintView.currentBrush = Color.RED
                currentColor(paintView.currentBrush)
            }
        }
        blueBtn.setOnClickListener {
            if (!paintView.selectMode) {
                paintView.currentBrush = Color.BLUE
                currentColor(paintView.currentBrush)
            }
        }
        blackBtn.setOnClickListener {
            if (!paintView.selectMode) {
                paintView.currentBrush = Color.BLACK
                currentColor(paintView.currentBrush)
            }
        }
        clearBtn.setOnClickListener {
            if (!paintView.selectMode) {
                viewModel.clearPath()
                paintView.pathList.clear()
                MainActivity.path.reset()
            }
        }

        val paintViewInclude = view.findViewById<FrameLayout>(R.id.paint_view_include)
        paintView = paintViewInclude.findViewById(R.id.paint_view)
        paintView = view.findViewById(R.id.paint_view)
        paintView.setCustomEventListener(this)

        viewModel.modelAnswer.observe(viewLifecycleOwner, observer)
    }

    private fun currentColor(color: Int){
        MainActivity.strokePaint.color = color
        MainActivity.path = Path()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProvider(this)[PaintViewModel::class.java]
    }

    override fun onPathAdded(path: CustomPath) {
        viewModel.addPath(path)
    }

    override fun onStrokeSelected(pos: PointF) {
        isStrokeSelected = true
        strokePosition.x = pos.x
        strokePosition.y = pos.y
        answerBtn.visibility = View.VISIBLE
    }

    override fun onStrokeDeselected() {
        answerBtn.visibility = View.GONE
    }

    fun onGptRequest() {
        if (isStrokeSelected) {
            isStrokeSelected = false
            paintView.isSelect = false

            val bitmap = paintView.saveToPNG()
            question = OCR_API(bitmap)

            answerBtn.visibility = View.GONE
            viewModel.queryGPT(0, question, PointF(0F, 0F))
        }
    }

    private fun gptRequest(q: String, view: TextView) {
        val mediaType: MediaType = "application/json; charset=utf-8".toMediaType()
        val okHttpClient = OkHttpClient()
        val json = "{" +
                "  \"model\": \"gpt-3.5-turbo\"," +
                "  \"messages\": [{\"role\": \"user\"," +
                "  \"content\": \"$q\"}]" +
                "}"

        val requestBody: RequestBody = json.toRequestBody(mediaType)
        val request: Request =
            Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader(
                    "Authorization",
                    BuildConfig.API_KEY
                )
                .post(requestBody)
                .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val json_obj = JSONObject(response.body?.string())
                val json_array = json_obj.optJSONArray("choices")

                val json_text = json_array.getJSONObject(0).getString("message")
                val json_obj2 = JSONObject(json_text)
                val json_text2 = json_obj2.getString("content")

                println(json_text2)
                mainActivity.runOnUiThread {
                    view.text = json_text2
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                mainActivity.runOnUiThread {
                    Toast.makeText(mainActivity, "GPT request failed...", Toast.LENGTH_LONG).show()
                }
            }
        })
    }

  fun OCR_API(bitmap: Bitmap):String{
      val base64Image: String = encodeImageToBase64(bitmap)

      // 서버로 이미지 전송
      return sendImageToServer(base64Image)
    }


    private fun encodeImageToBase64(imageBitmap: Bitmap): String {
        val baos = ByteArrayOutputStream()
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val imageBytes = baos.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }
    private fun sendImageToServer(base64Image: String):String {
        var response = ""
        try {
            val thread = Thread {
                try {
                    val url = URL("http://10.0.1.82:5002/upload")
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true

                    // JSON 데이터 생성
                    val requestData = JSONObject()
                    requestData.put("image", base64Image)

                    // 데이터 전송
                    val outputStream = DataOutputStream(connection.outputStream)
                    outputStream.writeBytes(requestData.toString())
                    outputStream.flush()
                    outputStream.close()

                    // 응답 코드 확인
                    val responseCode = connection.responseCode
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        // 응답 데이터 읽기
                        val inputStream = connection.inputStream
                        response = readInputStream(inputStream)
                        println(response)

                        // 서버에서 보낸 응답 처리
                        handleServerResponse(response)


                    } else {
                        println("POST request failed. Response Code: $responseCode")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            thread.start()
            thread.join()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return response
    }


    @Throws(IOException::class)
    private fun readInputStream(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val stringBuilder = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            stringBuilder.append(line)
        }
        reader.close()
        return stringBuilder.toString()
    }

    private fun handleServerResponse(response: String) {
        Log.d("Server Response", response)
    }
}