package co.smartreceipts.android.filters;

import org.json.JSONException;
import org.json.JSONObject;

import co.smartreceipts.android.model.ReceiptRow;

public class ReceiptIsExpensableFilter implements Filter<ReceiptRow> {

	public ReceiptIsExpensableFilter() {
		// empty
	}

	public ReceiptIsExpensableFilter(JSONObject json) throws JSONException {
		// empty
	}

	@Override
	public boolean accept(ReceiptRow t) {
		return t.isExpensable();
	}

	@Override
	public JSONObject getJsonRepresentation() throws JSONException {
		final JSONObject json = new JSONObject();
		json.put(FilterFactory.CLASS_NAME, this.getClass().getName());
		return json;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + getClass().hashCode();
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		// only compare by class since this filter has no parameters
		return (obj != null && getClass() == obj.getClass());
	}
}
