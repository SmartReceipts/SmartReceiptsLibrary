package co.smartreceipts.android.purchases;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.annotation.NonNull;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.purchases.model.ConsumablePurchase;
import co.smartreceipts.android.purchases.model.InAppPurchase;
import co.smartreceipts.android.purchases.model.ManagedProduct;
import co.smartreceipts.android.purchases.model.Subscription;
import co.smartreceipts.android.purchases.wallet.PurchaseWallet;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyCollectionOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class PurchaseManagerTest {

    // Service response codes
    private static final int RESULT_OK = 0;
    private static final int RESULT_USER_CANCELED = 1;
    private static final int RESULT_BILLING_UNAVAILABLE = 3;
    private static final int RESULT_ITEM_UNAVAILABLE = 4;
    private static final int RESULT_DEVELOPER_ERROR = 5;
    private static final int RESULT_ERROR = 6;
    private static final int RESULT_ITEM_ALREADY_OWNED = 7;
    private static final int RESULT_ITEM_NOT_OWNED = 8;

    // Purchase state codes
    private static final int PURCHASE_STATE_PURCHASED = 0;
    private static final int PURCHASE_STATE_CANCELLED = 1;
    private static final int PURCHASE_STATE_REFUNDED = 2;

    // Metadata
    private static final String PURCHASE_TOKEN = "012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689";
    private static final String IN_APP_DATA_SIGNATURE = "012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689==";

    // Class under test
    PurchaseManager purchaseManager;

    Application application = RuntimeEnvironment.application;

    ShadowApplication shadowApplication = Shadows.shadowOf(application);

    String packageName = RuntimeEnvironment.application.getPackageName();

    @Mock
    PurchaseWallet purchaseWallet;

    @Mock
    Analytics analytics;

    @Mock
    IBinder binder;

    @Mock
    IInAppBillingService inAppBillingService;

    @Mock
    ConsumablePurchase consumablePurchase;

    @Mock
    SubscriptionEventsListener listener1, listener2, listener3;

    @Captor
    ArgumentCaptor<Bundle> bundleCaptor;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(binder.queryLocalInterface("com.android.vending.billing.IInAppBillingService")).thenReturn(inAppBillingService);
        shadowApplication.setComponentNameAndServiceForBindService(new ComponentName("com.android.vending.billing", "InAppBillingService"), binder);

        purchaseManager = new PurchaseManager(application, purchaseWallet, analytics, Schedulers.immediate(), Schedulers.immediate());
        purchaseManager.addEventListener(listener1);
        purchaseManager.addEventListener(listener2);
        purchaseManager.addEventListener(listener3);
        purchaseManager.removeEventListener(listener3);
    }

    @Test
    public void onCreate() {
        purchaseManager.initialize(application);
        verifyInAppBillingServiceConnected();
    }

    @Test
    public void initializeThrowsRemoteException() throws Exception {
        // Configure
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenThrow(new RemoteException());

        // Test
        purchaseManager.initialize(application);

        // Verify
        verifyInAppBillingServiceConnected();
        verifyZeroInteractions(purchaseWallet);
    }

    @Test
    public void initializeResponseError() throws Exception {
        // Configure
        final Bundle getPurchasesResponse = new Bundle();
        getPurchasesResponse.putInt("RESPONSE_CODE", RESULT_ERROR);
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenReturn(getPurchasesResponse);

        // Test
        purchaseManager.initialize(application);

        // Verify
        verifyInAppBillingServiceConnected();
        verifyZeroInteractions(purchaseWallet);
    }

    @Test
    public void initializeWithNoneOwned() throws Exception {
        // Configure
        final Bundle getPurchasesResponse = new Bundle();
        getPurchasesResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", new ArrayList<String>());
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_DATA_LIST", new ArrayList<String>());
        getPurchasesResponse.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", new ArrayList<String>());
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenReturn(getPurchasesResponse);

        // Test
        purchaseManager.initialize(application);
        verifyInAppBillingServiceConnected();

        verify(purchaseWallet).updatePurchasesInWallet(Collections.<ManagedProduct>emptySet());
        verifyNoMoreInteractions(purchaseWallet);
    }

    @Test
    public void initializeWithCancelledOrder() throws Exception {
        // Configure
        final Bundle getPurchasesResponse = new Bundle();
        getPurchasesResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseItem(InAppPurchase.SmartReceiptsPlus))));
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_DATA_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseData(InAppPurchase.SmartReceiptsPlus, PURCHASE_STATE_CANCELLED))));
        getPurchasesResponse.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", new ArrayList<>(Collections.singletonList(getInAppDataSignature())));
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenReturn(getPurchasesResponse);

        // Test
        purchaseManager.initialize(application);
        verifyInAppBillingServiceConnected();

        verify(purchaseWallet).updatePurchasesInWallet(Collections.<ManagedProduct>emptySet());
        verifyNoMoreInteractions(purchaseWallet);
    }

    @Test
    public void initializeWithRefundedOrder() throws Exception {
        // Configure
        final Bundle getPurchasesResponse = new Bundle();
        getPurchasesResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseItem(InAppPurchase.SmartReceiptsPlus))));
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_DATA_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseData(InAppPurchase.SmartReceiptsPlus, PURCHASE_STATE_REFUNDED))));
        getPurchasesResponse.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", new ArrayList<>(Collections.singletonList(getInAppDataSignature())));
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenReturn(getPurchasesResponse);

        // Test
        purchaseManager.initialize(application);
        verifyInAppBillingServiceConnected();

        verify(purchaseWallet).updatePurchasesInWallet(Collections.<ManagedProduct>emptySet());
        verifyNoMoreInteractions(purchaseWallet);
    }

    @Test
    public void initializeWithSomeOwned() throws Exception {
        // Configure
        final Bundle getPurchasesResponse = new Bundle();
        getPurchasesResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_ITEM_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseItem(InAppPurchase.SmartReceiptsPlus))));
        getPurchasesResponse.putStringArrayList("INAPP_PURCHASE_DATA_LIST", new ArrayList<>(Collections.singletonList(getInAppPurchaseData(InAppPurchase.SmartReceiptsPlus, PURCHASE_STATE_PURCHASED))));
        getPurchasesResponse.putStringArrayList("INAPP_DATA_SIGNATURE_LIST", new ArrayList<>(Collections.singletonList(getInAppDataSignature())));
        when(inAppBillingService.getPurchases(3, packageName, "subs", null)).thenReturn(getPurchasesResponse);

        // Test
        purchaseManager.initialize(application);
        verifyInAppBillingServiceConnected();

        final ManagedProduct managedProduct = new Subscription(InAppPurchase.SmartReceiptsPlus, PURCHASE_TOKEN, IN_APP_DATA_SIGNATURE);
        verify(purchaseWallet).updatePurchasesInWallet(Collections.singleton(managedProduct));
        verifyNoMoreInteractions(purchaseWallet);
    }

    @Test
    public void getAvailableSubscriptionsThrowsRemoteException() throws Exception {
        // Configure
        final Bundle getSkuDetailsResponse = new Bundle();
        getSkuDetailsResponse.putInt("RESPONSE_CODE", RESULT_ERROR);
        when(inAppBillingService.getSkuDetails(eq(3), eq(packageName), eq("subs"), bundleCaptor.capture())).thenThrow(new RemoteException());

        // Test
        final TestSubscriber<Set<InAppPurchase>> testSubscriber = new TestSubscriber<>();
        purchaseManager.getAvailableSubscriptions().subscribe(testSubscriber);

        // Verify
        testSubscriber.assertNoValues();
        testSubscriber.assertNotCompleted();
        testSubscriber.assertError(RemoteException.class);
        verifyZeroInteractions(purchaseWallet);
        assertEquals(bundleCaptor.getValue().getStringArrayList("ITEM_ID_LIST"), InAppPurchase.getSubscriptionSkus());
    }

    @Test
    public void getAvailableSubscriptionsResponseError() throws Exception {
        // Configure
        final Bundle getSkuDetailsResponse = new Bundle();
        getSkuDetailsResponse.putInt("RESPONSE_CODE", RESULT_ERROR);
        when(inAppBillingService.getSkuDetails(eq(3), eq(packageName), eq("subs"), bundleCaptor.capture())).thenReturn(getSkuDetailsResponse);

        // Test
        final TestSubscriber<Set<InAppPurchase>> testSubscriber = new TestSubscriber<>();
        purchaseManager.getAvailableSubscriptions().subscribe(testSubscriber);

        // Verify
        testSubscriber.assertNoValues();
        testSubscriber.assertNotCompleted();
        testSubscriber.assertError(Exception.class);
        verifyZeroInteractions(purchaseWallet);
        assertEquals(bundleCaptor.getValue().getStringArrayList("ITEM_ID_LIST"), InAppPurchase.getSubscriptionSkus());
    }

    @Test
    public void getAvailableSubscriptionsWithNoneAvailable() throws Exception {
        // Configure
        final Bundle getSkuDetailsResponse = new Bundle();
        getSkuDetailsResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getSkuDetailsResponse.putStringArrayList("DETAILS_LIST", new ArrayList<String>());
        when(inAppBillingService.getSkuDetails(eq(3), eq(packageName), eq("subs"), bundleCaptor.capture())).thenReturn(getSkuDetailsResponse);

        // Test
        final TestSubscriber<Set<InAppPurchase>> testSubscriber = new TestSubscriber<>();
        purchaseManager.getAvailableSubscriptions().subscribe(testSubscriber);

        // Verify
        testSubscriber.assertValue(Collections.<InAppPurchase>emptySet());
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        verify(purchaseWallet, never()).addPurchaseToWallet(any(ManagedProduct.class));
        verify(purchaseWallet, never()).updatePurchasesInWallet(anyCollectionOf(ManagedProduct.class));
        verify(purchaseWallet, never()).removePurchaseFromWallet(any(InAppPurchase.class));
        assertEquals(bundleCaptor.getValue().getStringArrayList("ITEM_ID_LIST"), InAppPurchase.getSubscriptionSkus());
    }

    @Test
    public void getAvailableSubscriptionsWithSomeAvailable() throws Exception {
        // Configure
        final Bundle getSkuDetailsResponse = new Bundle();
        getSkuDetailsResponse.putInt("RESPONSE_CODE", RESULT_OK);
        getSkuDetailsResponse.putStringArrayList("DETAILS_LIST", new ArrayList<>(Collections.singletonList(getSkuDetails(InAppPurchase.SmartReceiptsPlus))));
        when(inAppBillingService.getSkuDetails(eq(3), eq(packageName), eq("subs"), bundleCaptor.capture())).thenReturn(getSkuDetailsResponse);

        // Test
        final TestSubscriber<Set<InAppPurchase>> testSubscriber = new TestSubscriber<>();
        purchaseManager.getAvailableSubscriptions().subscribe(testSubscriber);

        // Verify
        testSubscriber.assertValue(Collections.singleton(InAppPurchase.SmartReceiptsPlus));
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
        verify(purchaseWallet, never()).addPurchaseToWallet(any(ManagedProduct.class));
        verify(purchaseWallet, never()).updatePurchasesInWallet(anyCollectionOf(ManagedProduct.class));
        verify(purchaseWallet, never()).removePurchaseFromWallet(any(InAppPurchase.class));
        assertEquals(bundleCaptor.getValue().getStringArrayList("ITEM_ID_LIST"), InAppPurchase.getSubscriptionSkus());
    }

    @Test
    public void consumePurchaseThrowsRemoteException() throws Exception {
        // Configure
        when(consumablePurchase.getPurchaseToken()).thenReturn(PURCHASE_TOKEN);
        when(inAppBillingService.consumePurchase(3, packageName, PURCHASE_TOKEN)).thenThrow(new RemoteException());

        // Test
        final TestSubscriber<Void> testSubscriber = new TestSubscriber<>();
        purchaseManager.consumePurchase(consumablePurchase).subscribe(testSubscriber);

        // Verify
        testSubscriber.assertNoValues();
        testSubscriber.assertNotCompleted();
        testSubscriber.assertError(RemoteException.class);
    }

    @Test
    public void consumePurchaseFails() throws Exception {
        // Configure
        when(consumablePurchase.getPurchaseToken()).thenReturn(PURCHASE_TOKEN);
        when(inAppBillingService.consumePurchase(3, packageName, PURCHASE_TOKEN)).thenReturn(RESULT_ERROR);

        // Test
        final TestSubscriber<Void> testSubscriber = new TestSubscriber<>();
        purchaseManager.consumePurchase(consumablePurchase).subscribe(testSubscriber);

        // Verify
        testSubscriber.assertNoValues();
        testSubscriber.assertNotCompleted();
        testSubscriber.assertError(Exception.class);
    }

    @Test
    public void consumePurchaseSucceeds() throws Exception {
        // Configure
        when(consumablePurchase.getPurchaseToken()).thenReturn(PURCHASE_TOKEN);
        when(inAppBillingService.consumePurchase(3, packageName, PURCHASE_TOKEN)).thenReturn(RESULT_OK);

        // Test
        final TestSubscriber<Void> testSubscriber = new TestSubscriber<>();
        purchaseManager.consumePurchase(consumablePurchase).subscribe(testSubscriber);

        // Verify
        testSubscriber.assertNoValues();
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    private void verifyInAppBillingServiceConnected() {
        final Intent intent = shadowApplication.getNextStartedService();
        assertNotNull(intent);
        assertEquals(intent.getAction(), "com.android.vending.billing.InAppBillingService.BIND");
        assertFalse(shadowApplication.getBoundServiceConnections().isEmpty());
        // TODO: Verify activity lifecylce callbacks are working with custom shadow
    }

    @NonNull
    private String getInAppPurchaseItem(@NonNull InAppPurchase inAppPurchase) throws Exception {
        return inAppPurchase.getSku();
    }

    @NonNull
    private String getInAppPurchaseData(@NonNull InAppPurchase inAppPurchase, int purchaseState) throws Exception {
        // https://developer.android.com/google/play/billing/billing_reference.html
        final JSONObject json = new JSONObject();
        json.put("autoRenewing", true);
        json.put("orderId", "orderId");
        json.put("packageName", packageName);
        json.put("productId", inAppPurchase.getSku());
        json.put("purchaseTime", 1234567890123L);
        json.put("purchaseState", purchaseState);
        json.put("developerPayload", "1234567890");
        json.put("purchaseToken", PURCHASE_TOKEN);
        return json.toString();
    }

    @NonNull
    private String getInAppDataSignature() throws Exception {
        return IN_APP_DATA_SIGNATURE;
    }

    @NonNull
    private String getSkuDetails(@NonNull InAppPurchase inAppPurchase) throws Exception {
        // https://developer.android.com/google/play/billing/billing_reference.html
        final JSONObject json = new JSONObject();
        json.put("productId", inAppPurchase.getSku());
        json.put("type", "subs"); //TODO
        json.put("price", "$4.99");
        json.put("price_amount_micros", 4990000);
        json.put("price_currency_code", "USD");
        json.put("title", "title");
        json.put("description", "description");
        return json.toString();
    }
}