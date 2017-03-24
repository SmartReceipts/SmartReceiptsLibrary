package co.smartreceipts.android.sync.widget.backups;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.DialogFragment;

import com.google.common.base.Preconditions;

import co.smartreceipts.android.R;
import co.smartreceipts.android.SmartReceiptsApplication;
import co.smartreceipts.android.activities.NavigationHandler;
import co.smartreceipts.android.sync.BackupProvidersManager;
import co.smartreceipts.android.sync.model.RemoteBackupMetadata;

public class DeleteRemoteBackupDialogFragment extends DialogFragment implements DialogInterface.OnClickListener {

    private static final String ARG_BACKUP_METADATA = "arg_backup_metadata";

    private BackupProvidersManager mBackupProvidersManager;
    private RemoteBackupMetadata mBackupMetadata;

    public static DeleteRemoteBackupDialogFragment newInstance(@NonNull RemoteBackupMetadata remoteBackupMetadata) {
        final DeleteRemoteBackupDialogFragment fragment = new DeleteRemoteBackupDialogFragment();
        final Bundle args = new Bundle();
        args.putParcelable(ARG_BACKUP_METADATA, remoteBackupMetadata);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackupProvidersManager = ((SmartReceiptsApplication)getActivity().getApplication()).getBackupProvidersManager();
        mBackupMetadata = getArguments().getParcelable(ARG_BACKUP_METADATA);
        Preconditions.checkNotNull(mBackupMetadata, "This class requires that a RemoteBackupMetadata instance be provided");
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.dialog_remote_backup_delete_title);
        if (mBackupMetadata.getSyncDeviceId().equals(mBackupProvidersManager.getDeviceSyncId())) {
            builder.setMessage(getString(R.string.dialog_remote_backup_delete_message_this_device));
        } else {
            builder.setMessage(getString(R.string.dialog_remote_backup_delete_message, mBackupMetadata.getSyncDeviceName()));
        }
        builder.setCancelable(true);
        builder.setPositiveButton(R.string.dialog_remote_backup_delete_positive, this);
        builder.setNegativeButton(android.R.string.cancel, this);
        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialogInterface, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            new NavigationHandler(getActivity()).showDialog(DeleteRemoteBackupProgressDialogFragment.newInstance(mBackupMetadata));
        }
        dismiss();
    }
}
