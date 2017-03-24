package co.smartreceipts.android.purchases.wallet;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Collections;

import co.smartreceipts.android.purchases.model.InAppPurchase;
import co.smartreceipts.android.purchases.model.ManagedProduct;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class DefaultPurchaseWalletTest {

    private static final String PURCHASE_TOKEN = "012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689";
    private static final String IN_APP_DATA_SIGNATURE = "012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689012345689ABCDEF012345689ABCDEF012345689ABCDEF012345689==";

    // Class under test
    DefaultPurchaseWallet defaultPurchaseWallet;

    SharedPreferences preferences;

    @Mock
    ManagedProduct managedProduct;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(managedProduct.getInAppPurchase()).thenReturn(InAppPurchase.SmartReceiptsPlus);
        when(managedProduct.getPurchaseToken()).thenReturn(PURCHASE_TOKEN);
        when(managedProduct.getInAppDataSignature()).thenReturn(IN_APP_DATA_SIGNATURE);

        preferences = PreferenceManager.getDefaultSharedPreferences(RuntimeEnvironment.application);
        defaultPurchaseWallet = new DefaultPurchaseWallet(preferences);
    }

    @After
    public void tearDown() {
        preferences.edit().clear().apply();
    }

    @Test
    public void emptyPurchases() {
        assertFalse(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
    }

    @Test
    public void singlePurchase() {
        defaultPurchaseWallet.addPurchaseToWallet(managedProduct);

        assertTrue(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.singleton(InAppPurchase.SmartReceiptsPlus.getSku()));
        assertEquals(preferences.getString("pro_sku_3_purchaseToken", null), PURCHASE_TOKEN);
        assertEquals(preferences.getString("pro_sku_3_inAppDataSignature", null), IN_APP_DATA_SIGNATURE);
    }

    @Test
    public void updatePurchases() {
        defaultPurchaseWallet.updatePurchasesInWallet(Collections.singleton(managedProduct));

        assertTrue(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.singleton(InAppPurchase.SmartReceiptsPlus.getSku()));
        assertEquals(preferences.getString("pro_sku_3_purchaseToken", null), PURCHASE_TOKEN);
        assertEquals(preferences.getString("pro_sku_3_inAppDataSignature", null), IN_APP_DATA_SIGNATURE);
    }

    @Test
    public void removeMissingPurchase() {
        defaultPurchaseWallet.removePurchaseFromWallet(InAppPurchase.SmartReceiptsPlus);

        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.<String>emptySet());
        assertFalse(preferences.contains("pro_sku_3_purchaseToken"));
        assertFalse(preferences.contains("pro_sku_3_inAppDataSignature"));
    }

    @Test
    public void ensureAddedPurchaseIsPersisted() {
        defaultPurchaseWallet.addPurchaseToWallet(managedProduct);
        final PurchaseWallet newWallet = new DefaultPurchaseWallet(preferences);

        assertTrue(newWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertTrue(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.singleton(InAppPurchase.SmartReceiptsPlus.getSku()));
        assertEquals(preferences.getString("pro_sku_3_purchaseToken", null), PURCHASE_TOKEN);
        assertEquals(preferences.getString("pro_sku_3_inAppDataSignature", null), IN_APP_DATA_SIGNATURE);
    }

    @Test
    public void ensureUpdatedPurchaseListIsPersisted() {
        // First add it
        defaultPurchaseWallet.addPurchaseToWallet(managedProduct);

        // Then revoke it
        defaultPurchaseWallet.updatePurchasesInWallet(Collections.<ManagedProduct>emptySet());
        final PurchaseWallet newWallet = new DefaultPurchaseWallet(preferences);

        assertFalse(newWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertFalse(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.emptySet());
        assertFalse(preferences.contains("pro_sku_3_purchaseToken"));
        assertFalse(preferences.contains("pro_sku_3_inAppDataSignature"));
    }

    @Test
    public void ensureRemovedPurchaseIsPersisted() {
        defaultPurchaseWallet.addPurchaseToWallet(managedProduct);
        defaultPurchaseWallet.removePurchaseFromWallet(InAppPurchase.SmartReceiptsPlus);

        final PurchaseWallet newWallet = new DefaultPurchaseWallet(preferences);

        assertFalse(newWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertFalse(defaultPurchaseWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));
        assertEquals(preferences.getStringSet("key_sku_set", Collections.<String>emptySet()), Collections.emptySet());
        assertFalse(preferences.contains("pro_sku_3_purchaseToken"));
        assertFalse(preferences.contains("pro_sku_3_inAppDataSignature"));
    }

    @Test
    public void upgradeFrom_V_4_2_0_249_WhenWeDidNotPersistTokenOrSignature() {
        // Historically, we only used to save the sku set and not the token or signature
        preferences.edit().putStringSet("key_sku_set", Collections.singleton(InAppPurchase.SmartReceiptsPlus.getSku())).apply();
        final PurchaseWallet newWallet = new DefaultPurchaseWallet(preferences);

        assertTrue(newWallet.hasActivePurchase(InAppPurchase.SmartReceiptsPlus));

        // Update from Google InAppBilling
        newWallet.updatePurchasesInWallet(Collections.singleton(managedProduct));

        // Verify that we've now save the extra params
        assertEquals(preferences.getString("pro_sku_3_purchaseToken", null), PURCHASE_TOKEN);
        assertEquals(preferences.getString("pro_sku_3_inAppDataSignature", null), IN_APP_DATA_SIGNATURE);
    }

}