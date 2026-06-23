package com.example.smartblindstick

import android.content.Context
import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FaqFragment : Fragment() {

    private lateinit var adapter: FaqAdapter
    private val faqList = mutableListOf<Faq>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_faq, container, false)

        val faqRecyclerView: RecyclerView = view.findViewById(R.id.faqRecyclerView)
        faqRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Initial empty adapter
        adapter = FaqAdapter(faqList)
        faqRecyclerView.adapter = adapter

        loadAndTranslateFaq()

        return view
    }

    private fun loadAndTranslateFaq() {
        // 1. Get the target language code
        val prefs = requireActivity().getSharedPreferences("NazarSettings", Context.MODE_PRIVATE)
        val targetLangCode = prefs.getString("app_lang_code", "en") ?: "en"

        // 2. The Original English Data
        val rawEnglishData = listOf(
            Faq(
                "1. What is the Smart Blind Stick?",
                "The Smart Blind Stick is an assistive device designed to help visually impaired users move safely and independently. It uses sensors to detect obstacles, unsafe surfaces, sudden falls, and emergency situations, then sends alerts through vibration, buzzer, and the mobile app."
            ),
            Faq(
                "2. How does the Smart Blind Stick work?",
                "The stick continuously reads data from its sensors using the ESP32 microcontroller. The LiDAR sensor detects obstacles ahead, the water sensor detects wet or slippery surfaces, the accelerometer tracks movement and sudden tilt or fall conditions, and the emergency switch sends an emergency alert instantly. This data is processed in real time and sent to Firebase, where the app displays the current status."
            ),
            Faq(
                "3. What does the app show?",
                "The app can show obstacle distance in millimeters, obstacle status (safe, warning, or danger), water detection status, accelerometer values (X, Y, Z), emergency alert status, live sensor updates from Firebase, and user safety notifications."
            ),
            Faq(
                "4. What is the obstacle distance shown in the app?",
                "The obstacle distance shown in the app is the distance measured by the LiDAR sensor. It is displayed in millimeters (mm) so the user or caregiver can know how close an object is to the stick. For example: 1200 mm = safe distance, 600 mm = caution, 250 mm = obstacle very close."
            ),
            Faq(
                "5. What happens when an obstacle is detected?",
                "When the LiDAR sensor detects an obstacle within the danger range, the stick gives a vibration alert, the buzzer may sound depending on the condition, the app updates the obstacle status, and the distance value is shown in real time. This helps the user avoid collisions before physical contact."
            ),
            Faq(
                "6. Why is LiDAR used instead of only an ultrasonic sensor?",
                "LiDAR provides more accurate distance measurement, faster response, better real-time obstacle detection, stable readings in millimeters, and improved performance for close obstacle awareness. That is why this project uses LiDAR for more precise sensing in the app."
            ),
            Faq(
                "7. What does the water detection feature do?",
                "The water sensor detects wet surfaces, puddles, or slippery areas. If water is detected, the stick alerts the user, the app shows 'Water Detected', and a warning can be displayed to avoid unsafe movement. This helps prevent slips and accidents."
            ),
            Faq(
                "8. What are the X, Y, and Z values in the app?",
                "The X, Y, and Z values come from the accelerometer. They represent movement and tilt of the stick in three directions: X-axis for left/right movement, Y-axis for forward/backward movement, and Z-axis for up/down or vertical motion. These values help detect sudden shaking, unusual tilt, fall-like conditions, and abnormal movement patterns."
            ),
            Faq(
                "9. Why are accelerometer values shown in the app?",
                "The accelerometer values are shown so that the app can monitor the stick’s motion in real time, detect sudden falls or unusual movement, help in safety analysis, and provide advanced alerts in future updates. This makes the system smarter than a normal obstacle-only stick."
            ),
            Faq(
                "10. What is the emergency switch in the Smart Blind Stick?",
                "The emergency switch (rocker switch or emergency button) is a manual safety feature. If the user feels unsafe or needs immediate help, pressing the switch sends an emergency signal to Firebase, and the app immediately shows an emergency alert."
            ),
            Faq(
                "11. What happens when the emergency switch is pressed?",
                "When the emergency switch is activated, the stick sends an emergency status to Firebase, the app receives the update instantly, the app shows an Emergency Alert, and a caregiver or connected user can know that help may be needed. This feature is useful during falls, getting lost, unsafe road conditions, medical emergencies, and panic situations."
            ),
            Faq(
                "12. Is the data updated in real time in the app?",
                "Yes. The app receives live data through Firebase Realtime Database. Whenever the ESP32 updates sensor values, the app refreshes automatically, the latest obstacle distance is shown, water and emergency status are updated, and accelerometer values change live."
            ),
            Faq(
                "13. Why is Firebase used in this project?",
                "Firebase is used because it allows real-time data synchronization between ESP32 and the app, easy cloud-based monitoring, fast updates without manual refresh, reliable communication between hardware and mobile application, and easy expansion for notifications and caregiver support."
            ),
            Faq(
                "14. Can a caregiver also monitor the user using the app?",
                "Yes. If the app is shared or connected properly, a caregiver or family member can monitor obstacle alerts, emergency status, sensor updates, and overall safety conditions of the user. This makes the system useful not only for the user but also for guardians."
            ),
            Faq(
                "15. What is the use of vibration and buzzer alerts if the app already shows data?",
                "The app is useful for monitoring, but the user needs instant physical alerts while walking. That is why the stick itself uses a vibration motor for tactile warning and a buzzer for sound warning. The app acts as a monitoring and support system, while the stick gives the immediate real-world alert."
            ),
            Faq(
                "16. What if the app is not connected? Will the stick still work?",
                "Yes. The stick can still perform its core safety functions even if the app is not connected. Obstacle detection still works, vibration and buzzer alerts still work, water detection still works, and the emergency switch can still trigger local logic. However, the app will not receive live updates until the Firebase connection is restored."
            ),
            Faq(
                "17. Is this app only for visually impaired users?",
                "Not necessarily. The app can also be useful for family members, caregivers, guardians, project demo evaluators, and developers monitoring sensor data. The app acts as both a support tool and a monitoring dashboard."
            ),
            Faq(
                "18. What are the main features of the Smart Blind Stick app?",
                "Main app features include live obstacle distance display, real-time obstacle warning, water detection status, accelerometer X/Y/Z values, emergency alert status, Firebase-based live updates, simple user-friendly interface, and safety monitoring for caregivers."
            ),
            Faq(
                "19. What are the benefits of using this app with the Smart Blind Stick?",
                "Benefits include real-time safety monitoring, better awareness of sensor status, easy emergency visibility, remote observation by family or caregivers, improved confidence and reliability, and a clear view of stick condition and environment data."
            ),
            Faq(
                "20. How is this system better than a normal blind stick?",
                "A normal blind stick only detects obstacles after physical contact. This Smart Blind Stick system detects obstacles before contact, measures actual distance in millimeters, detects water or slippery surfaces, tracks motion using an accelerometer, supports emergency alerts, sends live data to the app, and improves safety and independence."
            )
        )

        // 3. If English, show immediately
        if (targetLangCode == "en") {
            faqList.clear()
            faqList.addAll(rawEnglishData)
            adapter.notifyDataSetChanged()
            return
        }

        // 4. If Hindi/Marathi/Urdu, Prepare and Translate
        TranslationManager.prepareTranslator(targetLangCode, onReady = {
            val translatedItems = mutableListOf<Faq>()
            var count = 0

            rawEnglishData.forEach { item ->
                TranslationManager.translateText(item.question) { translatedQ ->
                    TranslationManager.translateText(item.answer) { translatedA ->
                        translatedItems.add(Faq(translatedQ, translatedA))
                        count++

                        // Once all 20 are done, update UI
                        if (count == rawEnglishData.size) {
                            faqList.clear()
                            faqList.addAll(translatedItems)
                            requireActivity().runOnUiThread {
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
            }
        }, onError = {
            // Fallback to English if translation fails
            faqList.addAll(rawEnglishData)
            adapter.notifyDataSetChanged()
        })
    }
}