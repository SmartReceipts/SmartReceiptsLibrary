package co.smartreceipts.android.autocomplete

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import co.smartreceipts.android.R
import android.widget.TextView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView


/**
 * Modifies the core [ArrayAdapter] contract to address a bug that is specific to auto-completion
 */
class AutoCompleteArrayAdapter<Type>(context: Context,
                                     autoCompleteResults: ArrayList<AutoCompleteResult<Type>>,
                                     private val listener: ClickListener)
    : ArrayAdapter<AutoCompleteResult<Type>>(context, R.layout.auto_complete_view, autoCompleteResults) {

    interface ClickListener {
        fun onClick(removeAutoCompleteResult: Boolean, position: Int)
    }

    /**
     * Note: We override the default ArrayAdapter$ArrayFilter logic here, since this filter object's
     * [Filter.publishResults] method call will invalidate this adapter if our count is ever 0. This
     * introduces a issue if the user types all the way to the end of the results and then deletes a
     * character or two, since we'll now be using an invalidated set of results. As a result, we have
     * overridden this method to instead call [notifyDataSetChanged] if the [getCount] result is 0
     * when this method is called
     */
    override fun notifyDataSetInvalidated() {
        if (count != 0) {
            super.notifyDataSetInvalidated()
        } else {
            super.notifyDataSetChanged()
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val listItem = convertView ?: LayoutInflater.from(context).inflate(R.layout.auto_complete_view, parent, false)

        val result = getItem(position)

        val name = listItem.findViewById(R.id.auto_complete_name) as TextView
        name.text = result.displayName
        name.setOnClickListener {
            listener.onClick(false, position)
        }

        val image = listItem.findViewById(R.id.imgAutoCompleteDelete) as ImageView
        image.setOnClickListener {
            listener.onClick(true, position)
        }
        return listItem
    }
}