package co.smartreceipts.android.fragments;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ListFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import co.smartreceipts.android.SmartReceiptsApplication;
import wb.android.flex.Flex;

public class WBListFragment extends ListFragment {

	private SmartReceiptsApplication mApplication;
//	private DateManager mDateManager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mApplication = getSmartReceiptsApplication();
	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mApplication = null;
	}

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        if (getParentFragment() != null) {
            getParentFragment().startActivityForResult(intent, requestCode);
        } else {
            super.startActivityForResult(intent, requestCode);
        }
    }

    public void requestPermissionsWithPossibleChildFragment(@NonNull String[] permissions, int requestCode) {
        if (getParentFragment() != null) {
            getParentFragment().requestPermissions(permissions, requestCode);
        } else {
            this.requestPermissions(permissions, requestCode);
        }
    }

	protected String getFlexString(Flex flex, int id) {
		if (isAdded()) {
			return flex.getString(getActivity(), id);
		}
		else {
			return "";
		}
	}

    public final void setSupportActionBar(@Nullable Toolbar toolbar) {
        ((AppCompatActivity)getActivity()).setSupportActionBar(toolbar);
    }

    @Nullable
    public final ActionBar getSupportActionBar() {
        return ((AppCompatActivity)getActivity()).getSupportActionBar();
    }

	public SmartReceiptsApplication getSmartReceiptsApplication() {
		if (mApplication == null) {
			if (getActivity() != null) {
				final Application application = getActivity().getApplication();
				if (application instanceof SmartReceiptsApplication) {
					mApplication = (SmartReceiptsApplication) application;
				}
				else {
					throw new RuntimeException("The Application must be an instance a SmartReceiptsApplication");
				}
			}
		}
		return mApplication;
	}

}