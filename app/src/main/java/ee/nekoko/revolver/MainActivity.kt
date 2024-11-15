package ee.nekoko.revolver

import android.content.SharedPreferences
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.telephony.SubscriptionManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private var isPlaying = true
    private var countDownTimer: CountDownTimer? = null
    private lateinit var intervalInput: EditText
    private lateinit var saveButton: Button
    private lateinit var resultText: TextView
    private lateinit var nextSwitch: TextView
    private lateinit var fab: FloatingActionButton
    private lateinit var simCheckboxesContainer: LinearLayout
    private var intervalInSeconds: Long = 0
    private var nextSwitchTime: Long = 0 // don't init
    private var handler: Handler = Handler(Looper.getMainLooper()) // To run tasks every second
    private var runnable: Runnable? = null
    private var simSlots: Int = 0
    private var simSlotIds: MutableMap<String, Int> = mutableMapOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val subscriptionManager = SubscriptionManager.from(applicationContext)
        simSlots = subscriptionManager.getActiveSubscriptionInfoCountMax()
        intervalInput = findViewById(R.id.intervalInput)
        saveButton = findViewById(R.id.saveButton)
        resultText = findViewById(R.id.resultText)
        nextSwitch = findViewById(R.id.nextSwitch)
        fab = findViewById(R.id.fab)
        simCheckboxesContainer = findViewById(R.id.simCheckboxesContainer)
        sharedPreferences = getSharedPreferences("eSimPreferences", MODE_PRIVATE)
        intervalInSeconds = sharedPreferences.getLong("interval", 120)
        window.statusBarColor = resources.getColor(R.color.primary)

        startRecurringTimer()

        fab.setOnClickListener {
            isPlaying = !isPlaying
            updateFABIcon(fab)
            // Start or stop your eSIM switching work based on the play/pause state
            if (isPlaying) {
                Log.i("FAB", "Enqueued request in $intervalInSeconds seconds")
                enqueue()
            } else {
                WorkManager.getInstance(applicationContext).cancelAllWork()
            }
        }

        initialize()
        intervalInput.setText(intervalInSeconds.toString())
        resultText.text = "Switching eSIM every $intervalInSeconds seconds."
        saveButton.setOnClickListener {
            // Get the user input as a string
            val inputText = intervalInput.text.toString()

            // Validate the input
            if (inputText.isNotEmpty()) {
                intervalInSeconds = inputText.toLong()
                if (intervalInSeconds >= 10) {
                    resultText.text = "Switching eSIM every $intervalInSeconds seconds."
                    val editor = sharedPreferences.edit()
                    editor.putLong("interval", intervalInSeconds)
                    editor.apply()
                    enqueue()
                } else {
                    // Invalid input (not a positive number)
                    Toast.makeText(this, "Please enter a number greater than 10.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Input is empty
                Toast.makeText(this, "Please enter an interval.", Toast.LENGTH_SHORT).show()
            }
        }
        Log.e("Main", "Main has run")
        enqueue()

    }

    // Method to dynamically add SIM checkboxes (SIM1 to SIM5)
    private fun enqueue() {
        WorkManager.getInstance(applicationContext).cancelAllWork()
        val oneTimeWorkRequest = OneTimeWorkRequestBuilder<SwitchWorker>().addTag("SwitchWorker").setInitialDelay(intervalInSeconds, TimeUnit.SECONDS).build()
        WorkManager.getInstance(applicationContext).enqueue(oneTimeWorkRequest)
        nextSwitchTime = System.currentTimeMillis() + intervalInSeconds * 1000
        val editor = sharedPreferences.edit()
        editor.putLong("nextSwitch", nextSwitchTime)
        editor.apply()
    }


    private fun initialize() {
        for (i in 1..simSlots) {
            val isChecked = sharedPreferences.getBoolean("SIM$i", true)
            val checkBox = CheckBox(this)
            checkBox.text = "SIM$i"
            checkBox.id = View.generateViewId()
            checkBox.isChecked = isChecked
            checkBox.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, // Width = wrap_content
                LinearLayout.LayoutParams.WRAP_CONTENT  // Height = wrap_content
            )

            simSlotIds["SIM$i"] = checkBox.id
            simCheckboxesContainer.addView(checkBox)
        }
    }

    private fun updateFABIcon(fab: FloatingActionButton) {
        if (isPlaying) {
            fab.setImageResource(android.R.drawable.ic_media_pause)
        } else {
            fab.setImageResource(android.R.drawable.ic_media_play)
        }
    }

    private fun startRecurringTimer() {
        runnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val timeRemaining = ((sharedPreferences.getLong("nextSwitch", currentTime) - currentTime) / 1000)
                if (isPlaying) {
                    nextSwitch.setText("Next switch in $timeRemaining seconds")
                } else {
                    nextSwitch.setText("Switching paused.")
                }

                for (i in 1..simSlots) {
                    if (simSlotIds["SIM$i"] != null) {
                        val simSlotN: CheckBox = findViewById(simSlotIds["SIM$i"]!!)
                        if (sharedPreferences.getBoolean("SIM$i", true) != simSlotN.isChecked) {
                            val edit = sharedPreferences.edit()
                            edit.putBoolean("SIM$i", simSlotN.isChecked)
                            edit.apply()
                        }
                        simSlotN.setText("SIM$i: ${sharedPreferences.getString("next_SIM$i", "Pending Switch")}")
                    }
                }
                // Post the runnable to run again after 1 second
                handler.postDelayed(this, 1000)
            }
        }

        // Start the recurring task
        handler.post(runnable!!)
    }

    override fun onPause() {
        super.onPause()
        countDownTimer?.cancel()
        handler.removeCallbacks(runnable!!)
    }

    override fun onResume() {
        super.onResume()
        startRecurringTimer()
    }
}