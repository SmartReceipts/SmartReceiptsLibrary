package co.smartreceipts.android.ocr;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Preconditions;

import java.io.File;

import javax.inject.Inject;

import co.smartreceipts.android.apis.ApiValidationException;
import co.smartreceipts.android.apis.hosts.ServiceManager;
import co.smartreceipts.android.aws.s3.S3Manager;
import co.smartreceipts.android.di.scopes.ApplicationScope;
import co.smartreceipts.android.identity.IdentityManager;
import co.smartreceipts.android.ocr.apis.OcrService;
import co.smartreceipts.android.ocr.apis.model.OcrResponse;
import co.smartreceipts.android.ocr.apis.model.RecongitionRequest;
import co.smartreceipts.android.ocr.purchases.OcrPurchaseTracker;
import co.smartreceipts.android.ocr.push.OcrPushMessageReceiver;
import co.smartreceipts.android.ocr.push.OcrPushMessageReceiverFactory;
import co.smartreceipts.android.ocr.widget.alert.OcrProcessingStatus;
import co.smartreceipts.android.push.PushManager;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.settings.catalog.UserPreference;
import co.smartreceipts.android.utils.Feature;
import co.smartreceipts.android.utils.FeatureFlags;
import co.smartreceipts.android.utils.log.Logger;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;

@ApplicationScope
public class OcrInteractor {

    private static final String OCR_FOLDER = "ocr/";

    private final Context context;
    private final S3Manager s3Manager;
    private final IdentityManager identityManager;
    private final ServiceManager ocrServiceManager;
    private final PushManager pushManager;
    private final UserPreferenceManager userPreferenceManager;
    private final OcrPurchaseTracker ocrPurchaseTracker;
    private final OcrPushMessageReceiverFactory pushMessageReceiverFactory;
    private final Feature ocrFeature;
    private final BehaviorSubject<OcrProcessingStatus> ocrProcessingStatusSubject = BehaviorSubject.createDefault(OcrProcessingStatus.Idle);

    @Inject
    public OcrInteractor(@NonNull Context context, @NonNull S3Manager s3Manager, @NonNull IdentityManager identityManager,
                         @NonNull ServiceManager serviceManager, @NonNull PushManager pushManager, @NonNull OcrPurchaseTracker ocrPurchaseTracker,
                         @NonNull UserPreferenceManager userPreferenceManager) {
        this(context, s3Manager, identityManager, serviceManager, pushManager, ocrPurchaseTracker, userPreferenceManager, new OcrPushMessageReceiverFactory(), FeatureFlags.Ocr);
    }

    @VisibleForTesting
    OcrInteractor(@NonNull Context context, @NonNull S3Manager s3Manager, @NonNull IdentityManager identityManager,
                  @NonNull ServiceManager serviceManager, @NonNull PushManager pushManager, @NonNull OcrPurchaseTracker ocrPurchaseTracker,
                  @NonNull UserPreferenceManager userPreferenceManager, @NonNull OcrPushMessageReceiverFactory pushMessageReceiverFactory,
                  @NonNull Feature ocrFeature) {
        this.context = Preconditions.checkNotNull(context.getApplicationContext());
        this.s3Manager = Preconditions.checkNotNull(s3Manager);
        this.identityManager = Preconditions.checkNotNull(identityManager);
        this.ocrServiceManager = Preconditions.checkNotNull(serviceManager);
        this.pushManager = Preconditions.checkNotNull(pushManager);
        this.ocrPurchaseTracker = Preconditions.checkNotNull(ocrPurchaseTracker);
        this.userPreferenceManager = Preconditions.checkNotNull(userPreferenceManager);
        this.pushMessageReceiverFactory = Preconditions.checkNotNull(pushMessageReceiverFactory);
        this.ocrFeature = Preconditions.checkNotNull(ocrFeature);
    }

    public void initialize() {
        ocrPurchaseTracker.initialize();
    }

    @NonNull
    public Observable<OcrResponse> scan(@NonNull File file) {
        Preconditions.checkNotNull(file);
        ocrProcessingStatusSubject.onNext(OcrProcessingStatus.Idle);
        if (ocrFeature.isEnabled() && identityManager.isLoggedIn() && ocrPurchaseTracker.hasAvailableScans()) {
            Logger.info(OcrInteractor.this, "Initiating scan of {}.", file);
            final OcrPushMessageReceiver ocrPushMessageReceiver = pushMessageReceiverFactory.get();
            ocrProcessingStatusSubject.onNext(OcrProcessingStatus.UploadingImage);
            return s3Manager.upload(file, OCR_FOLDER)
                    .doOnSubscribe(disposable -> pushManager.registerReceiver(ocrPushMessageReceiver))
                    .subscribeOn(Schedulers.io())
                    .flatMap(s3Url -> {
                        Logger.debug(OcrInteractor.this, "S3 upload completed. Preparing url for delivery to our APIs.");
                        if (s3Url != null && s3Url.indexOf(OCR_FOLDER) > 0) {
                            return Observable.just(s3Url.substring(s3Url.indexOf(OCR_FOLDER)));
                        } else {
                            return Observable.error(new ApiValidationException("Failed to receive a valid url: " + s3Url));
                        }
                    })
                    .flatMap(s3Url -> {
                        Logger.debug(OcrInteractor.this, "Uploading OCR request for processing");
                        final boolean incognito = userPreferenceManager.get(UserPreference.Misc.OcrIncognitoMode);
                        // TODO: incognito
                        ocrProcessingStatusSubject.onNext(OcrProcessingStatus.PerformingScan);
                        return ocrServiceManager.getService(OcrService.class).scanReceipt(new RecongitionRequest(s3Url));
                    })
                    .flatMap(recognitionResponse -> {
                        if (recognitionResponse != null && recognitionResponse.getRecognition() != null && recognitionResponse.getRecognition().getId() != null) {
                            return Observable.just(recognitionResponse.getRecognition().getId());
                        } else {
                            return Observable.error(new ApiValidationException("Failed to receive a valid recognition response."));
                        }
                    })
                    .flatMap(recognitionId -> {
                        Logger.debug(OcrInteractor.this, "Awaiting completion of recognition request {}.", recognitionId);
                        return ocrPushMessageReceiver.getOcrPushResponse()
                                .onErrorReturn(throwable -> {
                                    Logger.warn(OcrInteractor.this, "Ocr request timed out. Attempting to get response as is");
                                    return new Object();
                                })
                                .map(o -> recognitionId);
                    })
                    .flatMap(recognitionId -> {
                        Logger.debug(OcrInteractor.this, "Scan completed. Fetching results for {}.", recognitionId);
                        ocrProcessingStatusSubject.onNext(OcrProcessingStatus.RetrievingResults);
                        return ocrServiceManager.getService(OcrService.class).getRecognitionResult(recognitionId);
                    })
                    .flatMap(recognitionResponse -> {
                        Logger.debug(OcrInteractor.this, "Parsing OCR Response");
                        if (recognitionResponse != null &&
                                recognitionResponse.getRecognition() != null &&
                                recognitionResponse.getRecognition().getData() != null &&
                                recognitionResponse.getRecognition().getData().getRecognitionData() != null) {
                            return Observable.just(recognitionResponse.getRecognition().getData().getRecognitionData());
                        } else {
                            return Observable.error(new ApiValidationException("Failed to receive a valid recognition response."));
                        }
                    })
                    .onErrorReturnItem(new OcrResponse())
                    .doOnTerminate(() -> {
                        ocrProcessingStatusSubject.onNext(OcrProcessingStatus.Idle);
                        pushManager.unregisterReceiver(ocrPushMessageReceiver);
                    });
            // TODO: Handle subsequent deletion of s3 file
        } else {
            Logger.debug(OcrInteractor.this, "Ignoring ocr scan of as: isFeatureEnabled = {}, isLoggedIn = {}, hasAvailableScans = {}.", ocrFeature.isEnabled(), identityManager.isLoggedIn(), ocrPurchaseTracker.hasAvailableScans());
            return Observable.just(new OcrResponse());
        }
    }

    @NonNull
    public Observable<OcrProcessingStatus> getOcrProcessingStatus() {
        return ocrProcessingStatusSubject
                .subscribeOn(Schedulers.computation());
    }
}
