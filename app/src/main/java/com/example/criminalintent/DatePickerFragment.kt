package com.example.criminalintent

import android.app.DatePickerDialog
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.DatePicker
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar

private const val ARG_DATE = "date"

class DatePickerFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val date = arguments?.getLong(ARG_DATE)?.let { Date(it) }
        val calender = Calendar.getInstance()
        if (date != null) {
            calender.time = date
        }
        val initYear = calender.get(Calendar.YEAR)
        val initMonth = calender.get(Calendar.MONTH)
        val initDay = calender.get(Calendar.DAY_OF_MONTH)
        val dateListener =
            DatePickerDialog.OnDateSetListener { _: DatePicker, year: Int, month: Int, day: Int ->
                val resultDate = GregorianCalendar(year, month, day).time
                val bundle = Bundle().apply {
                    putLong("date", resultDate.time)
                }
//                callbacks?.onDateSelected(resultDate)
//                parentFragmentManager.fragments.let { fragments ->
//                    (fragments as Callbacks).onDateSelected(
//                        resultDate
//                    )
//                }
                parentFragmentManager.setFragmentResult("requestKey", bundle)
            }

        return DatePickerDialog(
            requireContext(),
            dateListener,
            initYear,
            initMonth,
            initDay
        )
    }

    companion object {
        fun newInstance(date: Long): DatePickerFragment {
            val args = Bundle().apply {
                putSerializable(ARG_DATE, date)
            }
            return DatePickerFragment().apply {
                arguments = args
            }
        }
    }
}