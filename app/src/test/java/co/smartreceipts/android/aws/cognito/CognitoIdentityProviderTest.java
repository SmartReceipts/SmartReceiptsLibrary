package co.smartreceipts.android.aws.cognito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;

import co.smartreceipts.android.identity.IdentityManager;
import co.smartreceipts.android.identity.apis.me.Cognito;
import co.smartreceipts.android.identity.apis.me.MeResponse;
import co.smartreceipts.android.identity.apis.me.User;
import rx.Observable;
import rx.observers.TestSubscriber;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class CognitoIdentityProviderTest {

    private static final String TOKEN = "token";
    private static final String IDENTITY_ID = "identityId";
    private static final long EXPIRES_AT = 5;

    // Class under test
    CognitoIdentityProvider cognitoIdentityProvider;

    @Mock
    IdentityManager identityManager;

    @Mock
    LocalCognitoTokenStore localCognitoTokenStore;

    @Mock
    MeResponse meResponse;

    @Mock
    User user;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(meResponse.getUser()).thenReturn(user);
        when(localCognitoTokenStore.getCognitoToken()).thenReturn(new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT));

        cognitoIdentityProvider = new CognitoIdentityProvider(identityManager, localCognitoTokenStore);
    }

    @Test
    public void refreshCognitoTokenThrowsException() {
        when(identityManager.getMe()).thenReturn(Observable.<MeResponse>error(new IOException()));

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.refreshCognitoToken().subscribe(testSubscriber);

        verify(localCognitoTokenStore).persist(null);
        testSubscriber.assertNoValues();
        testSubscriber.assertNotCompleted();
        testSubscriber.assertError(IOException.class);
    }

    @Test
    public void refreshCognitoTokenReturnsNullUserResponse() {
        when(meResponse.getUser()).thenReturn(null);
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.refreshCognitoToken().subscribe(testSubscriber);

        verify(localCognitoTokenStore, times(2)).persist(null);
        testSubscriber.assertValue(null);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void refreshCognitoTokenIsValid() {
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));
        when(user.getCognitoToken()).thenReturn(TOKEN);
        when(user.getIdentityId()).thenReturn(IDENTITY_ID);
        when(user.getCognitoTokenExpiresAt()).thenReturn(EXPIRES_AT);

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.refreshCognitoToken().subscribe(testSubscriber);

        final Cognito cognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);
        verify(localCognitoTokenStore).persist(null);
        verify(localCognitoTokenStore).persist(cognito);
        testSubscriber.assertValue(cognito);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void prefetchNullToken() {
        final Cognito preCognito = null;
        when(localCognitoTokenStore.getCognitoToken()).thenReturn(preCognito);
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));
        when(user.getCognitoToken()).thenReturn(TOKEN);
        when(user.getIdentityId()).thenReturn(IDENTITY_ID);
        when(user.getCognitoTokenExpiresAt()).thenReturn(EXPIRES_AT);

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.prefetchCognitoTokenIfNeeded().subscribe(testSubscriber);

        final Cognito cognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);
        verify(localCognitoTokenStore).persist(null);
        verify(localCognitoTokenStore).persist(cognito);
        testSubscriber.assertValue(cognito);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void prefetchInvalidTokenWillNullToken() {
        final Cognito preCognito = new Cognito(null, IDENTITY_ID, EXPIRES_AT);;
        when(localCognitoTokenStore.getCognitoToken()).thenReturn(preCognito);
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));
        when(user.getCognitoToken()).thenReturn(TOKEN);
        when(user.getIdentityId()).thenReturn(IDENTITY_ID);
        when(user.getCognitoTokenExpiresAt()).thenReturn(EXPIRES_AT);

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.prefetchCognitoTokenIfNeeded().subscribe(testSubscriber);

        final Cognito cognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);
        verify(localCognitoTokenStore).persist(null);
        verify(localCognitoTokenStore).persist(cognito);
        testSubscriber.assertValue(cognito);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void prefetchInvalidTokenWillNullIdentityId() {
        final Cognito preCognito = new Cognito(TOKEN, null, EXPIRES_AT);;
        when(localCognitoTokenStore.getCognitoToken()).thenReturn(preCognito);
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));
        when(user.getCognitoToken()).thenReturn(TOKEN);
        when(user.getIdentityId()).thenReturn(IDENTITY_ID);
        when(user.getCognitoTokenExpiresAt()).thenReturn(EXPIRES_AT);

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.prefetchCognitoTokenIfNeeded().subscribe(testSubscriber);

        final Cognito cognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);
        verify(localCognitoTokenStore).persist(null);
        verify(localCognitoTokenStore).persist(cognito);
        testSubscriber.assertValue(cognito);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void prefetchValidToken() {
        final Cognito preCognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);;
        when(localCognitoTokenStore.getCognitoToken()).thenReturn(preCognito);

        final TestSubscriber<Cognito> testSubscriber = new TestSubscriber<>();
        cognitoIdentityProvider.prefetchCognitoTokenIfNeeded().subscribe(testSubscriber);
        
        verify(localCognitoTokenStore, never()).persist(any(Cognito.class));
        testSubscriber.assertValue(preCognito);
        testSubscriber.assertCompleted();
        testSubscriber.assertNoErrors();
    }

    @Test
    public void synchronouslyRefreshCognitoTokenThrowsException() {
        when(identityManager.getMe()).thenReturn(Observable.<MeResponse>error(new IOException()));

        assertEquals(null, cognitoIdentityProvider.synchronouslyRefreshCognitoToken());
    }

    @Test
    public void synchronouslyRefreshCognitoTokenReturnsNullUserResponse() {
        when(meResponse.getUser()).thenReturn(null);
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));

        assertEquals(null, cognitoIdentityProvider.synchronouslyRefreshCognitoToken());
    }

    @Test
    public void synchronouslyRefreshCognitoTokenIsValid() {
        when(identityManager.getMe()).thenReturn(Observable.just(meResponse));
        when(user.getCognitoToken()).thenReturn(TOKEN);
        when(user.getIdentityId()).thenReturn(IDENTITY_ID);
        when(user.getCognitoTokenExpiresAt()).thenReturn(EXPIRES_AT);

        final Cognito cognito = new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT);
        assertEquals(cognito, cognitoIdentityProvider.synchronouslyRefreshCognitoToken());
    }

    @Test
    public void getCachedCognitoToken() {
        assertEquals(new Cognito(TOKEN, IDENTITY_ID, EXPIRES_AT), cognitoIdentityProvider.getCachedCognitoToken());
        verify(localCognitoTokenStore).getCognitoToken();
    }

}