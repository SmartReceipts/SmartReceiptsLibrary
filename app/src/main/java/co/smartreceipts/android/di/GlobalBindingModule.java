package co.smartreceipts.android.di;

import co.smartreceipts.android.activities.SmartReceiptsActivity;
import co.smartreceipts.android.fragments.ReceiptMoveCopyDialogFragment;
import co.smartreceipts.android.fragments.SelectAutomaticBackupProviderDialogFragment;
import co.smartreceipts.android.images.CropImageActivity;
import co.smartreceipts.android.images.di.CropModule;
import co.smartreceipts.android.imports.intents.di.IntentImportInformationModule;
import co.smartreceipts.android.permissions.PermissionRequesterHeadlessFragment;
import co.smartreceipts.android.rating.FeedbackDialogFragment;
import co.smartreceipts.android.rating.RatingDialogFragment;
import co.smartreceipts.android.receipts.attacher.ReceiptAttachmentDialogFragment;
import co.smartreceipts.android.receipts.attacher.ReceiptRemoveAttachmentDialogFragment;
import co.smartreceipts.android.search.SearchActivity;
import co.smartreceipts.android.search.SearchModule;
import co.smartreceipts.android.settings.widget.SettingsActivity;
import co.smartreceipts.android.settings.widget.editors.categories.CategoriesListFragment;
import co.smartreceipts.android.settings.widget.editors.categories.CategoryEditorDialogFragment;
import co.smartreceipts.android.settings.widget.editors.columns.CSVColumnsListFragment;
import co.smartreceipts.android.settings.widget.editors.columns.PDFColumnsListFragment;
import co.smartreceipts.android.settings.widget.editors.payment.PaymentMethodsListFragment;
import co.smartreceipts.android.subscriptions.SubscriptionsActivity;
import co.smartreceipts.android.subscriptions.di.SubscriptionsModule;
import co.smartreceipts.android.sync.widget.backups.DeleteRemoteBackupProgressDialogFragment;
import co.smartreceipts.android.sync.widget.backups.DownloadRemoteBackupImagesProgressDialogFragment;
import co.smartreceipts.android.sync.widget.backups.ExportBackupWorkerProgressDialogFragment;
import co.smartreceipts.android.sync.widget.backups.ImportLocalBackupWorkerProgressDialogFragment;
import co.smartreceipts.android.sync.widget.backups.ImportRemoteBackupWorkerProgressDialogFragment;
import co.smartreceipts.android.sync.widget.backups.RenameRemoteBackupProgressDialogFragment;
import co.smartreceipts.core.di.scopes.ActivityScope;
import co.smartreceipts.core.di.scopes.FragmentScope;
import dagger.Module;
import dagger.android.ContributesAndroidInjector;

@Module
public abstract class GlobalBindingModule {

    @ActivityScope
    @ContributesAndroidInjector(modules = {
            SmartReceiptsActivityModule.class,
            SmartReceiptsActivityBindingModule.class,
            IntentImportInformationModule.class,
            SmartReceiptsActivityAdModule.class
    })
    public abstract SmartReceiptsActivity smartReceiptsActivity();

    @ActivityScope
    @ContributesAndroidInjector
    public abstract SettingsActivity settingsActivity();

    @ActivityScope
    @ContributesAndroidInjector(modules = CropModule.class)
    public abstract CropImageActivity cropImageActivity();

    @ActivityScope
    @ContributesAndroidInjector(modules = SearchModule.class)
    public abstract SearchActivity searchActivity();

    @ActivityScope
    @ContributesAndroidInjector(modules = SubscriptionsModule.class)
    public abstract SubscriptionsActivity subscriptionsActivity();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract CSVColumnsListFragment csvColumnsListFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract PDFColumnsListFragment pdfColumnsListFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract RenameRemoteBackupProgressDialogFragment renameRemoteBackupProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract DeleteRemoteBackupProgressDialogFragment deleteRemoteBackupProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract DownloadRemoteBackupImagesProgressDialogFragment downloadRemoteBackupImagesProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ExportBackupWorkerProgressDialogFragment exportBackupWorkerProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ImportLocalBackupWorkerProgressDialogFragment importLocalBackupWorkerProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ImportRemoteBackupWorkerProgressDialogFragment importRemoteBackupWorkerProgressDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract FeedbackDialogFragment feedbackDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract RatingDialogFragment ratingDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract PaymentMethodsListFragment paymentMethodsListFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract CategoriesListFragment categoriesListFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract CategoryEditorDialogFragment categoryEditorDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ReceiptMoveCopyDialogFragment receiptMoveCopyDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract SelectAutomaticBackupProviderDialogFragment selectAutomaticBackupProviderDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ReceiptAttachmentDialogFragment receiptAttachmentDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract ReceiptRemoveAttachmentDialogFragment receiptRemoveAttachmentDialogFragment();

    @FragmentScope
    @ContributesAndroidInjector
    public abstract PermissionRequesterHeadlessFragment permissionRequesterHeadlessFragment();

}
