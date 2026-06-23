package com.example.smartblindstick

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment

class LocationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val textView = TextView(requireContext())
        textView.text = "Location Page (Coming Soon)"
        textView.textSize = 20f
        textView.setPadding(40, 40, 40, 40)

        return textView
    }
}
