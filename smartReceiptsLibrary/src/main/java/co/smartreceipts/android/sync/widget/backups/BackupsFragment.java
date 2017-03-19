package co.smartreceipts.android.sync.widget.backups;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import javax.inject.Inject;

import co.smartreceipts.android.R;
import co.smartreceipts.android.activities.NavigationHandler;
import co.smartreceipts.android.activities.SmartReceiptsActivity;
import co.smartreceipts.android.fragments.SelectAutomaticBackupProviderDialogFragment;
import co.smartreceipts.android.fragments.WBFragment;
import co.smartreceipts.android.persistence.PersistenceManager;
import co.smartreceipts.android.purchases.PurchaseManager;
import co.smartreceipts.android.purchases.Subscription;
import co.smartreceipts.android.purchases.source.PurchaseSource;
import co.smartreceipts.android.purchases.wallet.PurchaseWallet;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.sync.BackupProviderChangeListener;
import co.smartreceipts.android.sync.BackupProvidersManager;
import co.smartreceipts.android.sync.model.RemoteBackupMetadata;
import co.smartreceipts.android.sync.network.NetworkManager;
import co.smartreceipts.android.sync.network.SupportedNetworkType;
import co.smartreceipts.android.sync.provider.SyncProvider;
import dagger.android.support.AndroidSupportInjection;
import rx.functions.Action1;
import rx.subscriptions.CompositeSubscription;

public class BackupsFragment extends WBFragment implements BackupProviderChangeListener {

    private static final int IMPORT_SMR_REQUEST_CODE = 50;

    @Inject
    PersistenceManager persistenceManager;

    @Inject
    PurchaseWallet purchaseWallet;

    private BackupProvidersManager backupProvidersManager;
    private NetworkManager networkManager;
    private RemoteBackupsDataCache remoteBackupsDataCache;
    private CompositeSubscription compositeSubscription;
    private NavigationHandler navigationHandler;

    private Toolbar toolbar;
    private View headerView;
    private View exportButton;
    private View importButton;
    private View backupConfigButton;
    private ImageView backupConfigButtonImage;
    private TextView backupConfigButtonText;
    private TextView warningTextView;
    private CheckBox wifiOnlyCheckbox;
    private View existingBackupsSection;
    private RecyclerView recyclerView;

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        backupProvidersManager = getSmartReceiptsApplication().getBackupProvidersManager();
        networkManager = getSmartReceiptsApplication().getNetworkManager();
        remoteBackupsDataCache = new RemoteBackupsDataCache(getFragmentManager(), getContext(),
                backupProvidersManager, networkManager, persistenceManager.getDatabase());
        navigationHandler = new NavigationHandler(getActivity());
    }

    @Nullable
    @Override
    @SuppressLint("InflateParams")
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.backups_list, container, false);
        recyclerView = (RecyclerView) rootView.findViewById(android.R.id.list);

        headerView = inflater.inflate(R.layout.backups_header, null);
        exportButton = headerView.findViewById(R.id.manual_backup_export);
        importButton = headerView.findViewById(R.id.manual_backup_import);
        warningTextView = (TextView) headerView.findViewById(R.id.auto_backup_warning);
        backupConfigButton = headerView.findViewById(R.id.automatic_backup_config_button);
        backupConfigButtonImage = (ImageView) headerView.findViewById(R.id.automatic_backup_config_button_image);
        backupConfigButtonText = (TextView) headerView.findViewById(R.id.automatic_backup_config_button_text);
        wifiOnlyCheckbox = (CheckBox) headerView.findViewById(R.id.auto_backup_wifi_only);
        existingBackupsSection = headerView.findViewById(R.id.existing_backups_section);

        exportButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                navigationHandler.showDialog(new ExportBackupDialogFragment());
            }
        });
        importButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(intent, getString(R.string.import_string)), IMPORT_SMR_REQUEST_CODE);
                } catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(getContext(), getString(R.string.error_no_file_intent_dialog_title), Toast.LENGTH_SHORT).show();
                }
            }
        });
        backupConfigButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final PurchaseManager purchaseManager;
                if (getActivity() instanceof SmartReceiptsActivity) {
                    final SmartReceiptsActivity smartReceiptsActivity = (SmartReceiptsActivity) getActivity();
                    purchaseManager = smartReceiptsActivity.getSubscriptionManager();
                } else {
                    purchaseManager = null;
                }

                if (purchaseManager != null && backupProvidersManager.getSyncProvider() == SyncProvider.None
                        && !purchaseWallet.hasSubscription(Subscription.SmartReceiptsPlus)) {
                    purchaseManager.queryBuyIntent(Subscription.SmartReceiptsPlus, PurchaseSource.AutomaticBackups);
                } else {
                    navigationHandler.showDialog(new SelectAutomaticBackupProviderDialogFragment());
                }
            }
        });
        wifiOnlyCheckbox.setChecked(persistenceManager.getPreferenceManager().get(UserPreference.Misc.AutoBackupOnWifiOnly));
        wifiOnlyCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                persistenceManager.getPreferenceManager().set(UserPreference.Misc.AutoBackupOnWifiOnly, checked);
                backupProvidersManager.setAndInitializeNetworkProviderType(checked ? SupportedNetworkType.WifiOnly : SupportedNetworkType.AllNetworks);
            }
        });
        return rootView;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView.setAdapter(new RemoteBackupsListAdapter(headerView, getActivity(),
                backupProvidersManager, persistenceManager.getPreferenceManager(), networkManager));
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.backups);
        }
        compositeSubscription = new CompositeSubscription();
        updateViewsForProvider(backupProvidersManager.getSyncProvider());
        backupProvidersManager.registerChangeListener(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == IMPORT_SMR_REQUEST_CODE) {
                if (data != null) {
                    navigationHandler.showDialog(ImportLocalBackupDialogFragment.newInstance(data.getData()));
                }
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return navigationHandler.navigateBack();
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onPause() {
        backupProvidersManager.unregisterChangeListener(this);
        compositeSubscription.unsubscribe();
        super.onPause();
    }

    @Override
    public void onProviderChanged(@NonNull SyncProvider newProvider) {
        // Clear out any existing subscriptions when we change providers
        compositeSubscription.unsubscribe();
        compositeSubscription = new CompositeSubscription();
        remoteBackupsDataCache.clearGetBackupsResults();

        updateViewsForProvider(newProvider);
    }

    public void updateViewsForProvider(@NonNull SyncProvider syncProvider) {
        if (isResumed()) {
            if (syncProvider == SyncProvider.None) {
                warningTextView.setText(R.string.auto_backup_warning_none);
                backupConfigButtonText.setText(R.string.auto_backup_configure);
                backupConfigButtonImage.setImageResource(R.drawable.ic_cloud_off_24dp);
                wifiOnlyCheckbox.setVisibility(View.GONE);
            } else if (syncProvider == SyncProvider.GoogleDrive) {
                warningTextView.setText(R.string.auto_backup_warning_drive);
                backupConfigButtonText.setText(R.string.auto_backup_source_google_drive);
                backupConfigButtonImage.setImageResource(R.drawable.ic_cloud_done_24dp);
                wifiOnlyCheckbox.setVisibility(View.VISIBLE);
            } else {
                throw new IllegalArgumentException("Unsupported sync provider type was specified");
            }

            compositeSubscription.add(remoteBackupsDataCache.getBackups(syncProvider)
                    .subscribe(new Action1<List<RemoteBackupMetadata>>() {
                        @Override
                        public void call(List<RemoteBackupMetadata> remoteBackupMetadatas) {
                            if (remoteBackupMetadatas.isEmpty()) {
                                existingBackupsSection.setVisibility(View.GONE);
                            } else {
                                existingBackupsSection.setVisibility(View.VISIBLE);
                            }
                            final RemoteBackupsListAdapter remoteBackupsListAdapter =
                                    new RemoteBackupsListAdapter(headerView, getActivity(),
                                            backupProvidersManager, persistenceManager.getPreferenceManager(), networkManager, remoteBackupMetadatas);
                            recyclerView.setAdapter(remoteBackupsListAdapter);
                        }
                    }));
        }
    }

}
