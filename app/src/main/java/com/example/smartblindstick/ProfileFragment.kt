package com.example.smartblindstick

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Base64
import android.util.Patterns
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.yalantis.ucrop.UCrop
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    private var contact1Number = ""
    private var contact2Number = ""
    private var contact3Number = ""
    private var pendingContactSlot = 1

    private lateinit var profileImage: ImageView
    private lateinit var cameraIconCard: MaterialCardView

    // Basic Info Variables
    private var currentName = ""
    private var currentEmail = ""
    private var currentPhone = ""
    private var currentCity = ""
    private var currentAddress = ""
    private var currentBase64Image: String? = null

    // Health Info Variables
    private var currentBloodGroup = ""
    private var currentGender = ""
    private var currentDob = ""
    private var currentHeight = ""
    private var currentWeight = ""
    private var currentDisease = ""
    private var currentMedicine = ""

    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let { processAndUploadPhoto(it) }
            }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { startUCrop(it) }
        }

    private val requestGalleryPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) pickImageLauncher.launch("image/*")
        }

    private val requestContactPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) openContactPicker()
        }

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val contactUri = result.data?.data ?: return@registerForActivityResult
                val cursor = requireContext().contentResolver.query(
                    contactUri, null, null, null, null
                )
                if (cursor != null && cursor.moveToFirst()) {
                    val nIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val dIdx =
                        cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nIdx != -1 && dIdx != -1) {
                        val num = cursor.getString(nIdx)
                        val nam = cursor.getString(dIdx)
                        database.updateChildren(
                            mapOf(
                                "emergencyContact${pendingContactSlot}Name" to nam,
                                "emergencyContact${pendingContactSlot}" to num
                            )
                        )
                    }
                    cursor.close()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        auth = FirebaseAuth.getInstance()
        val uid = auth.currentUser?.uid ?: return view
        database = FirebaseDatabase.getInstance().getReference("users").child(uid)

        // UI Bindings
        profileImage = view.findViewById(R.id.profileImage)
        cameraIconCard = view.findViewById(R.id.cameraIconCard)
        val avatarCard = view.findViewById<MaterialCardView>(R.id.avatarCard)

        // Basic Info Bindings
        val nameText = view.findViewById<TextView>(R.id.profileName)
        val nameValueText = view.findViewById<TextView>(R.id.profileNameValue)
        val emailText = view.findViewById<TextView>(R.id.profileEmail)
        val phoneText = view.findViewById<TextView>(R.id.profilePhone)
        val cityText = view.findViewById<TextView>(R.id.profileCity)
        val addressText = view.findViewById<TextView>(R.id.profileAddress)
        val btnEditPersonalInfo = view.findViewById<TextView>(R.id.btnEditPersonalInfo)

        // Health Info Bindings
        val healthBloodGroup = view.findViewById<TextView>(R.id.healthBloodGroup)
        val healthGender = view.findViewById<TextView>(R.id.healthGender)
        val healthDob = view.findViewById<TextView>(R.id.healthDob)
        val healthHeight = view.findViewById<TextView>(R.id.healthHeight)
        val healthWeight = view.findViewById<TextView>(R.id.healthWeight)
        val healthDisease = view.findViewById<TextView>(R.id.healthDisease)
        val healthMedicine = view.findViewById<TextView>(R.id.healthMedicine)
        val btnEditHealthInfo = view.findViewById<TextView>(R.id.btnEditHealthInfo)

        // Contacts Bindings
        val btnAddContact = view.findViewById<MaterialCardView>(R.id.btnAddContact)
        val emptyContactCard = view.findViewById<MaterialCardView>(R.id.emptyContactCard)
        val contactsContainerCard = view.findViewById<MaterialCardView>(R.id.contactsContainerCard)
        val contactRow1 = view.findViewById<LinearLayout>(R.id.contactRow1)
        val contactRow2 = view.findViewById<LinearLayout>(R.id.contactRow2)
        val contactRow3 = view.findViewById<LinearLayout>(R.id.contactRow3)
        val divider1 = view.findViewById<View>(R.id.contactDivider1)
        val divider2 = view.findViewById<View>(R.id.contactDivider2)
        val cName1 = view.findViewById<TextView>(R.id.profileContactName1)
        val cName2 = view.findViewById<TextView>(R.id.profileContactName2)
        val cName3 = view.findViewById<TextView>(R.id.profileContactName3)
        val cNum1 = view.findViewById<TextView>(R.id.profileContact1)
        val cNum2 = view.findViewById<TextView>(R.id.profileContact2)
        val cNum3 = view.findViewById<TextView>(R.id.profileContact3)
        val callBtn1 = view.findViewById<MaterialCardView>(R.id.callBtn1)
        val callBtn2 = view.findViewById<MaterialCardView>(R.id.callBtn2)
        val callBtn3 = view.findViewById<MaterialCardView>(R.id.callBtn3)
        val logoutButton = view.findViewById<MaterialButton>(R.id.logoutButton)

        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                // Fetch Basic Data
                currentName = snapshot.child("name").value?.toString() ?: "Not Set"
                currentEmail = snapshot.child("email").value?.toString() ?: "Not Set"
                currentPhone = snapshot.child("phone").value?.toString() ?: "Not Set"
                currentCity = snapshot.child("city").value?.toString() ?: "Not Set"
                currentAddress = snapshot.child("address").value?.toString() ?: "Not Set"
                currentBase64Image =
                    snapshot.child("profileImageBase64").value?.toString()

                // Fetch Health Data
                currentBloodGroup =
                    snapshot.child("bloodGroup").value?.toString() ?: "Not Set"
                currentGender = snapshot.child("gender").value?.toString() ?: "Not Set"
                currentDob = snapshot.child("dob").value?.toString() ?: "Not Set"
                currentHeight = snapshot.child("height").value?.toString() ?: "Not Set"
                currentWeight = snapshot.child("weight").value?.toString() ?: "Not Set"
                currentDisease =
                    snapshot.child("medicalDisease").value?.toString() ?: "None reported"
                currentMedicine =
                    snapshot.child("medicine").value?.toString() ?: "None reported"

                // Fetch Contacts — treat null, empty and "Not Set" uniformly
                contact1Number =
                    snapshot.child("emergencyContact1").value?.toString()?.trim() ?: ""
                contact2Number =
                    snapshot.child("emergencyContact2").value?.toString()?.trim() ?: ""
                contact3Number =
                    snapshot.child("emergencyContact3").value?.toString()?.trim() ?: ""

                // Apply Basic Data
                nameText.text = currentName
                nameValueText.text = currentName
                emailText.text = currentEmail
                phoneText.text = currentPhone
                cityText.text = currentCity
                addressText.text = currentAddress

                // Apply Health Data
                healthBloodGroup.text = currentBloodGroup
                healthGender.text = currentGender
                healthDob.text = currentDob
                healthHeight.text =
                    if (currentHeight == "Not Set") "Not Set" else "$currentHeight cm"
                healthWeight.text =
                    if (currentWeight == "Not Set") "Not Set" else "$currentWeight kg"
                healthDisease.text = currentDisease
                healthMedicine.text = currentMedicine

                // Apply Contacts
                cName1.text = snapshot.child("emergencyContact1Name").value?.toString()
                    ?: "Emergency Contact 1"
                cName2.text = snapshot.child("emergencyContact2Name").value?.toString()
                    ?: "Emergency Contact 2"
                cName3.text = snapshot.child("emergencyContact3Name").value?.toString()
                    ?: "Emergency Contact 3"
                cNum1.text = contact1Number.ifEmpty { "Not Set" }
                cNum2.text = contact2Number.ifEmpty { "Not Set" }
                cNum3.text = contact3Number.ifEmpty { "Not Set" }

                // Profile Image
                if (!currentBase64Image.isNullOrEmpty()) {
                    try {
                        val imageBytes = Base64.decode(currentBase64Image, Base64.DEFAULT)
                        val bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        if (bmp != null) {
                            profileImage.setImageBitmap(bmp)
                            profileImage.setPadding(0, 0, 0, 0)
                            profileImage.scaleType = ImageView.ScaleType.CENTER_CROP
                            profileImage.imageTintList = null
                            cameraIconCard.visibility = View.GONE
                        } else {
                            setDefaultProfileIcon()
                        }
                    } catch (_: Exception) {
                        setDefaultProfileIcon()
                    }
                } else {
                    setDefaultProfileIcon()
                }

                // Contact visibility — a contact slot is "filled" when it is non-empty and not "Not Set"
                val c1Filled = contact1Number.isNotEmpty() && contact1Number != "Not Set"
                val c2Filled = contact2Number.isNotEmpty() && contact2Number != "Not Set"
                val c3Filled = contact3Number.isNotEmpty() && contact3Number != "Not Set"
                val contactCount = listOf(c1Filled, c2Filled, c3Filled).count { it }

                emptyContactCard.visibility =
                    if (contactCount == 0) View.VISIBLE else View.GONE
                contactsContainerCard.visibility =
                    if (contactCount > 0) View.VISIBLE else View.GONE
                // Show "add" button only when there is at least one contact but slots remain
                btnAddContact.visibility =
                    if (contactCount in 1..2) View.VISIBLE else View.GONE

                contactRow1.visibility = if (c1Filled) View.VISIBLE else View.GONE
                contactRow2.visibility = if (c2Filled) View.VISIBLE else View.GONE
                contactRow3.visibility = if (c3Filled) View.VISIBLE else View.GONE

                // Divider between row1 and any row below it
                divider1.visibility =
                    if (c1Filled && (c2Filled || c3Filled)) View.VISIBLE else View.GONE
                // Divider between row2 and row3
                divider2.visibility =
                    if (c2Filled && c3Filled) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {}
        })

        // Avatar tap: view full-screen if photo exists, else open gallery
        avatarCard.setOnClickListener {
            if (!currentBase64Image.isNullOrEmpty()) showFullScreenImage(currentBase64Image!!)
            else checkGalleryPermission()
        }

        // Avatar long-press: change or remove photo
        avatarCard.setOnLongClickListener {
            if (!currentBase64Image.isNullOrEmpty()) {
                AlertDialog.Builder(requireContext())
                    .setItems(arrayOf("Change Photo", "Remove Photo")) { _, which ->
                        if (which == 0) checkGalleryPermission()
                        else database.child("profileImageBase64").removeValue()
                    }.show()
            } else {
                checkGalleryPermission()
            }
            true
        }

        btnEditPersonalInfo.setOnClickListener { showEditProfileBottomSheet() }
        btnEditHealthInfo.setOnClickListener { showEditHealthBottomSheet() }

        // Add-contact logic extracted to avoid lambda type issues
        val addContactAction = {
            when {
                contact1Number.isEmpty() || contact1Number == "Not Set" -> {
                    pendingContactSlot = 1; checkContactPermission()
                }
                contact2Number.isEmpty() || contact2Number == "Not Set" -> {
                    pendingContactSlot = 2; checkContactPermission()
                }
                contact3Number.isEmpty() || contact3Number == "Not Set" -> {
                    pendingContactSlot = 3; checkContactPermission()
                }
                else -> Toast.makeText(requireContext(), "All contact slots are full", Toast.LENGTH_SHORT).show()
            }
        }

        btnAddContact.setOnClickListener { addContactAction() }
        emptyContactCard.setOnClickListener { addContactAction() }

        contactRow1.setOnLongClickListener {
            if (contact1Number.isNotEmpty() && contact1Number != "Not Set")
                showDeleteContactDialog(1)
            true
        }
        contactRow2.setOnLongClickListener {
            if (contact2Number.isNotEmpty() && contact2Number != "Not Set")
                showDeleteContactDialog(2)
            true
        }
        contactRow3.setOnLongClickListener {
            if (contact3Number.isNotEmpty() && contact3Number != "Not Set")
                showDeleteContactDialog(3)
            true
        }

        callBtn1.setOnClickListener {
            if (contact1Number.isNotEmpty() && contact1Number != "Not Set")
                startActivity(Intent(Intent.ACTION_DIAL, "tel:$contact1Number".toUri()))
        }
        callBtn2.setOnClickListener {
            if (contact2Number.isNotEmpty() && contact2Number != "Not Set")
                startActivity(Intent(Intent.ACTION_DIAL, "tel:$contact2Number".toUri()))
        }
        callBtn3.setOnClickListener {
            if (contact3Number.isNotEmpty() && contact3Number != "Not Set")
                startActivity(Intent(Intent.ACTION_DIAL, "tel:$contact3Number".toUri()))
        }

        logoutButton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        return view
    }

    // ─── UCrop ───────────────────────────────────────────────────────────────

    private fun startUCrop(sourceUri: Uri) {
        try {
            val fileName = "profile_crop_${System.currentTimeMillis()}.jpg"
            val destinationUri = Uri.fromFile(File(requireContext().cacheDir, fileName))
            val options = UCrop.Options().apply {
                setCompressionFormat(Bitmap.CompressFormat.JPEG)
                setCompressionQuality(100)
                setHideBottomControls(false)
                setFreeStyleCropEnabled(false)
                setStatusBarColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setToolbarColor(ContextCompat.getColor(requireContext(), R.color.primary))
                setToolbarWidgetColor(Color.WHITE)
                setToolbarTitle("Edit Profile Photo")
                setRootViewBackgroundColor(Color.BLACK)
            }
            cropImageLauncher.launch(
                UCrop.of(sourceUri, destinationUri)
                    .withAspectRatio(1f, 1f)
                    .withOptions(options)
                    .getIntent(requireContext())
            )
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error opening cropper: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun processAndUploadPhoto(resultUri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(resultUri) ?: return
            val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return
            inputStream.close()
            val scaledBitmap = originalBitmap.scale(800, 800, true)
            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            val base64String = Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
            database.child("profileImageBase64").setValue(base64String)
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Upload failed", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── Default Icon ────────────────────────────────────────────────────────

    private fun setDefaultProfileIcon() {
        currentBase64Image = null
        // Uses ic_profile_avatar — a dedicated avatar drawable (no tint override needed)
        profileImage.setImageResource(R.drawable.ic_profile_avatar)
        profileImage.setPadding(0, 0, 0, 0)
        profileImage.imageTintList = null
        profileImage.scaleType = ImageView.ScaleType.CENTER_CROP
        cameraIconCard.visibility = View.VISIBLE
    }

    // ─── Full-screen Image Dialog ─────────────────────────────────────────────

    private fun showFullScreenImage(base64String: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val imageView = ImageView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.BLACK)
        }
        try {
            val bytes = Base64.decode(base64String, Base64.DEFAULT)
            imageView.setImageBitmap(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        } catch (_: Exception) {
            return
        }
        imageView.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(imageView)
        dialog.show()
    }

    // ─── Permissions ─────────────────────────────────────────────────────────

    private fun checkGalleryPermission() {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        )
            pickImageLauncher.launch("image/*")
        else
            requestGalleryPermissionLauncher.launch(permission)
    }

    private fun checkContactPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        ) openContactPicker()
        else requestContactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun openContactPicker() {
        pickContactLauncher.launch(
            Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        )
    }

    // ─── Edit Personal Info Bottom Sheet ─────────────────────────────────────

    @SuppressLint("InflateParams")
    private fun showEditProfileBottomSheet() {
        val bsd = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        bsd.setContentView(v)

        val eN = v.findViewById<TextInputEditText>(R.id.editName)
        val eE = v.findViewById<TextInputEditText>(R.id.editEmail)
        val eP = v.findViewById<TextInputEditText>(R.id.editPhone)
        val eC = v.findViewById<TextInputEditText>(R.id.editCity)
        val eA = v.findViewById<TextInputEditText>(R.id.editAddress)
        val bS = v.findViewById<MaterialButton>(R.id.btnSaveProfile)

        eN?.setText(if (currentName != "Not Set") currentName else "")
        eE?.setText(if (currentEmail != "Not Set") currentEmail else "")
        eP?.setText(if (currentPhone != "Not Set") currentPhone else "")
        eC?.setText(if (currentCity != "Not Set") currentCity else "")
        eA?.setText(if (currentAddress != "Not Set") currentAddress else "")

        bS?.setOnClickListener {
            val name = eN?.text.toString().trim()
            val email = eE?.text.toString().trim()
            val phone = eP?.text.toString().trim()
            val city = eC?.text.toString().trim()
            val address = eA?.text.toString().trim()

            if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(requireContext(), "Invalid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            database.updateChildren(
                mapOf(
                    "name" to name.ifEmpty { "Not Set" },
                    "email" to email.ifEmpty { "Not Set" },
                    "phone" to phone.ifEmpty { "Not Set" },
                    "city" to city.ifEmpty { "Not Set" },
                    "address" to address.ifEmpty { "Not Set" }
                )
            )
            bsd.dismiss()
        }
        bsd.show()
    }

    // ─── Edit Health Info Bottom Sheet ───────────────────────────────────────

    @SuppressLint("InflateParams")
    private fun showEditHealthBottomSheet() {
        val bsd = BottomSheetDialog(requireContext())
        val v = layoutInflater.inflate(R.layout.dialog_edit_health, null)
        bsd.setContentView(v)

        val editBloodGroup = v.findViewById<AutoCompleteTextView>(R.id.editBloodGroup)
        val editGender = v.findViewById<AutoCompleteTextView>(R.id.editGender)
        val editDob = v.findViewById<TextInputEditText>(R.id.editDob)
        val editHeight = v.findViewById<TextInputEditText>(R.id.editHeight)
        val editWeight = v.findViewById<TextInputEditText>(R.id.editWeight)
        val editDisease = v.findViewById<TextInputEditText>(R.id.editDisease)
        val editMedicine = v.findViewById<TextInputEditText>(R.id.editMedicine)
        val btnSaveHealth = v.findViewById<MaterialButton>(R.id.btnSaveHealth)

        // Dropdown adapters
        val bloodOptions = arrayOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        editBloodGroup.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, bloodOptions)
        )
        if (currentBloodGroup != "Not Set") editBloodGroup.setText(currentBloodGroup, false)

        val genderOptions = arrayOf("Male", "Female", "Other")
        editGender.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, genderOptions)
        )
        if (currentGender != "Not Set") editGender.setText(currentGender, false)

        // Prefill text fields (strip display suffixes stored separately)
        if (currentDob != "Not Set") editDob.setText(currentDob)
        if (currentHeight != "Not Set") editHeight.setText(currentHeight)
        if (currentWeight != "Not Set") editWeight.setText(currentWeight)
        if (currentDisease != "None reported") editDisease.setText(currentDisease)
        if (currentMedicine != "None reported") editMedicine.setText(currentMedicine)

        // Date picker for DOB
        editDob.setOnClickListener {
            val c = Calendar.getInstance()
            // If there is an existing date, pre-select it in the picker
            val existingDate = editDob.text.toString()
            if (existingDate.isNotEmpty()) {
                try {
                    val parts = existingDate.split("/")
                    if (parts.size == 3) {
                        c.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                    }
                } catch (_: Exception) { /* ignore, use today */ }
            }
            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    editDob.setText(
                        String.format(Locale.getDefault(), "%02d/%02d/%04d", d, m + 1, y)
                    )
                },
                c.get(Calendar.YEAR),
                c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Spinner for Height
        editHeight.setOnClickListener {
            val cur = editHeight.text.toString().toIntOrNull() ?: 170
            showNumberPickerDialog("Select Height (cm)", 50, 250, cur) { result ->
                editHeight.setText(result.toString())
            }
        }

        // Spinner for Weight
        editWeight.setOnClickListener {
            val cur = editWeight.text.toString().toIntOrNull() ?: 70
            showNumberPickerDialog("Select Weight (kg)", 20, 200, cur) { result ->
                editWeight.setText(result.toString())
            }
        }

        btnSaveHealth.setOnClickListener {
            database.updateChildren(
                mapOf(
                    "bloodGroup" to editBloodGroup.text.toString().ifEmpty { "Not Set" },
                    "gender" to editGender.text.toString().ifEmpty { "Not Set" },
                    "dob" to editDob.text.toString().ifEmpty { "Not Set" },
                    "height" to editHeight.text.toString().ifEmpty { "Not Set" },
                    "weight" to editWeight.text.toString().ifEmpty { "Not Set" },
                    "medicalDisease" to editDisease.text.toString().trim().ifEmpty { "None reported" },
                    "medicine" to editMedicine.text.toString().trim().ifEmpty { "None reported" }
                )
            )
            bsd.dismiss()
        }
        bsd.show()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun showNumberPickerDialog(
        title: String, min: Int, max: Int, current: Int, onResult: (Int) -> Unit
    ) {
        val picker = NumberPicker(requireContext()).apply {
            minValue = min
            maxValue = max
            value = if (current in min..max) current else min
            wrapSelectorWheel = false
        }
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(picker)
            .setPositiveButton("Select") { _, _ -> onResult(picker.value) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteContactDialog(slot: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("Remove Contact")
            .setMessage("Remove Emergency Contact $slot?")
            .setPositiveButton("Remove") { _, _ ->
                database.child("emergencyContact${slot}Name").removeValue()
                database.child("emergencyContact${slot}").removeValue()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}