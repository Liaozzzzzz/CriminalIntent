package com.example.criminalintent

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import java.io.File
import java.util.Date
import java.util.UUID

private const val TAG = "CrimeFragment"
private const val ARG_CRIME_ID = "crime_id"
private const val DIALOG_DATE = "DialogDate"
private const val DATE_FORMAT = "EEE, MMM, dd"

class CrimeFragment : Fragment() {
    private lateinit var crime: Crime
    private lateinit var photoFile: File
    private lateinit var photoUri: Uri
    private lateinit var titleField: EditText
    private lateinit var dateButton: Button
    private lateinit var solvedCheckBox: CheckBox
    private lateinit var reportButton: Button
    private lateinit var suspectButton: Button
    private lateinit var photoBtn: ImageButton
    private lateinit var photoView: ImageView

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { results ->
            Log.d(TAG, "拍照回调： $results")
            updatePhotoView()
        }

    private val requestDataLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, result.toString())
            if (result.resultCode != Activity.RESULT_OK || result.data == null) {
                Log.d(TAG, "result.data: ${result.data.toString()}")
                return@registerForActivityResult
            }

            val contractUri: Uri? = result.data!!.data
            val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
            Log.d(TAG, "contractUri: ${contractUri.toString()}")
            if (contractUri == null) {
                return@registerForActivityResult
            }
            val cursor =
                requireActivity().contentResolver.query(contractUri, queryFields, null, null, null)
            Log.d(TAG, "cursor: ${cursor.toString()}")
            cursor?.use {
                if (it.count == 0) {
                    Log.d(TAG, "it.count == 0")
                    return@registerForActivityResult
                }
                it.moveToFirst()
                val suspect = it.getString(0)
                crime.suspect = suspect
                crimeDetailViewModel.saveCrime(crime)
                suspectButton.text = suspect
            }
        }

    private val crimeDetailViewModel: CrimeDetailViewModel by lazy {
        ViewModelProvider(this)[CrimeDetailViewModel::class.java]
    }

    companion object {
        fun newInstance(crimeId: UUID): CrimeFragment {
            val args = Bundle().apply {
                putSerializable(ARG_CRIME_ID, crimeId)
            }
            return CrimeFragment().apply {
                arguments = args
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        crime = Crime()
        val crimeId: UUID? =
            arguments?.let { BundleCompat.getSerializable(it, ARG_CRIME_ID, UUID::class.java) }
//        val crimeId: UUID? = arguments?.getSerializable(ARG_CRIME_ID, UUID::class.java)
//        val crimeId: UUID = arguments?.getSerializable(ARG_CRIME_ID) as UUID
        Log.d(TAG, "args bundle crime ID: $crimeId")

        if (crimeId != null) {
            crimeDetailViewModel.loadCrime(crimeId)
        }

        parentFragmentManager.setFragmentResultListener(
            "requestKey",
            this
        ) { _, bundle ->
            val results = bundle.getLong("date")
            Log.i(TAG, results.toString())
            crime.date = Date(results)
            updateUI()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_crime, container, false)

        titleField = view.findViewById(R.id.crime_title)
        dateButton = view.findViewById(R.id.crime_date)
        solvedCheckBox = view.findViewById(R.id.crime_solved)
        reportButton = view.findViewById(R.id.crime_report)
        suspectButton = view.findViewById(R.id.crime_suspect)
        photoBtn = view.findViewById(R.id.crime_camera)
        photoView = view.findViewById(R.id.crime_photo)

        crimeDetailViewModel.crimeLiveData.observe(
            viewLifecycleOwner,
            Observer { crime ->
                crime?.let {
                    this.crime = crime
                    photoFile =
                        crimeDetailViewModel.getPhotoFile(crime)
                    photoUri =
                        FileProvider.getUriForFile(
                            requireActivity(),

                            "com.example.criminalintent.fileprovider",
                            photoFile
                        )

                    updateUI()
                }
            }
        )

        return view
    }

    override fun onStart() {
        super.onStart()
        val titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(
                sequence: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) {
                // This space intentionally left blank
            }

            override fun onTextChanged(
                sequence: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) {
                crime.title = sequence.toString()
            }

            override fun afterTextChanged(sequence: Editable?) {
                // This one too
            }
        }

        titleField.addTextChangedListener(titleWatcher)

        solvedCheckBox.apply {
            setOnCheckedChangeListener { _, isChecked ->
                crime.isSolved = isChecked
            }
        }

        dateButton.setOnClickListener {
            DatePickerFragment.newInstance(crime.date.time).apply {
                show(this@CrimeFragment.parentFragmentManager, DIALOG_DATE)
            }
        }

        reportButton.setOnClickListener {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, getCrimeReport())
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject))
            }.also { intent: Intent ->
                val chooserIntent =
                    Intent.createChooser(
                        intent,
                        getString(R.string.send_report)
                    )
                startActivity(chooserIntent)
            }
        }

        suspectButton.apply {
            val pickContactIntent =
                Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            setOnClickListener {
                requestDataLauncher.launch(pickContactIntent)
            }
            val packageManager: PackageManager =
                requireActivity().packageManager
            val resolvedActivity: ResolveInfo? =
                packageManager.resolveActivity(
                    pickContactIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
            if (resolvedActivity == null) {
                isEnabled = false
            }
        }

        photoBtn.apply {
            setOnClickListener {
                takePictureLauncher.launch(photoUri)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        crimeDetailViewModel.saveCrime(crime)
    }

    private fun updatePhotoView() {
        if (photoFile.exists()) {
            val bitmap = getScaledBitmap(
                photoFile.path,
                requireActivity()
            )
            photoView.setImageBitmap(bitmap)
        } else {
            photoView.setImageDrawable(null)
        }
    }


    private fun updateUI() {
        titleField.setText(crime.title)
        dateButton.text = crime.date.toString()
        solvedCheckBox.apply {
            isChecked = crime.isSolved
            jumpDrawablesToCurrentState()
        }
        if (crime.suspect.isNotEmpty()) {
            suspectButton.text = crime.suspect
        }
        updatePhotoView()
    }

    private fun getCrimeReport(): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }
        val dateString = DateFormat.format(
            DATE_FORMAT,
            crime.date
        ).toString()
        var suspect = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(
                R.string.crime_report_suspect,
                crime.suspect
            )
        }
        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspect
        )
    }
}