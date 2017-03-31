package co.smartreceipts.android.identity;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.common.base.Preconditions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import co.smartreceipts.android.analytics.Analytics;
import co.smartreceipts.android.analytics.events.Events;
import co.smartreceipts.android.apis.ApiValidationException;
import co.smartreceipts.android.apis.hosts.ServiceManager;
import co.smartreceipts.android.di.scopes.ApplicationScope;
import co.smartreceipts.android.identity.apis.login.LoginParams;
import co.smartreceipts.android.identity.apis.login.LoginPayload;
import co.smartreceipts.android.identity.apis.login.LoginResponse;
import co.smartreceipts.android.identity.apis.login.LoginService;
import co.smartreceipts.android.identity.apis.logout.LogoutResponse;
import co.smartreceipts.android.identity.apis.logout.LogoutService;
import co.smartreceipts.android.identity.apis.me.MeResponse;
import co.smartreceipts.android.identity.apis.me.MeService;
import co.smartreceipts.android.identity.apis.organizations.OrganizationsResponse;
import co.smartreceipts.android.identity.store.EmailAddress;
import co.smartreceipts.android.identity.store.IdentityStore;
import co.smartreceipts.android.identity.store.MutableIdentityStore;
import co.smartreceipts.android.identity.store.Token;
import co.smartreceipts.android.push.apis.me.UpdatePushTokensRequest;
import co.smartreceipts.android.settings.UserPreferenceManager;
import co.smartreceipts.android.utils.log.Logger;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.subjects.AsyncSubject;
import rx.subjects.BehaviorSubject;
import rx.subjects.Subject;

@ApplicationScope
public class IdentityManager implements IdentityStore {

    private final ServiceManager serviceManager;
    private final Analytics analytics;
    private final MutableIdentityStore mutableIdentityStore;
    private final OrganizationManager organizationManager;
    private final BehaviorSubject<Boolean> isLoggedInBehaviorSubject;
    private final Map<LoginParams, Subject<LoginResponse, LoginResponse>> loginMap = new ConcurrentHashMap<>();

    private Subject<LogoutResponse, LogoutResponse> logoutSubject;

    @Inject
    public IdentityManager(Analytics analytics,
                           UserPreferenceManager userPreferenceManager,
                           MutableIdentityStore mutableIdentityStore,
                           ServiceManager serviceManager) {

        this.serviceManager = serviceManager;
        this.analytics = analytics;
        this.mutableIdentityStore = mutableIdentityStore;
        this.organizationManager = new OrganizationManager(serviceManager, mutableIdentityStore, userPreferenceManager);
        this.isLoggedInBehaviorSubject = BehaviorSubject.create(isLoggedIn());

    }

    @Nullable
    @Override
    public EmailAddress getEmail() {
        return mutableIdentityStore.getEmail();
    }

    @Nullable
    @Override
    public Token getToken() {
        return mutableIdentityStore.getToken();
    }

    @Override
    public boolean isLoggedIn() {
        return mutableIdentityStore.isLoggedIn();
    }

    /**
     * @return an {@link Observable} relay that will only emit {@link Subscriber#onNext(Object)} calls
     * (and never {@link Subscriber#onCompleted()} or {@link Subscriber#onError(Throwable)} calls) under
     * the following circumstances:
     * <ul>
     * <li>When the app launches, it will emit {@code true} if logged in and {@code false} if not</li>
     * <li>When the user signs in, it will emit  {@code true}</li>
     * <li>When the user signs out, it will emit  {@code false}</li>
     * </ul>
     * <p>
     * Users of this class should expect a {@link BehaviorSubject} type behavior in which the current
     * state will always be emitted as soon as we subscribe
     * </p>
     */
    @NonNull
    public Observable<Boolean> isLoggedInStream() {
        return isLoggedInBehaviorSubject.asObservable();
    }

    public synchronized Observable<LoginResponse> logIn(@NonNull final LoginParams login) {
        Preconditions.checkNotNull(login.getEmail(), "A valid email must be provided to log-in");

        Logger.info(this, "Initiating user log-in");
        this.analytics.record(Events.Identity.UserLogin);

        final LoginService loginService = serviceManager.getService(LoginService.class);
        final LoginPayload request = new LoginPayload(login);

        Subject<LoginResponse, LoginResponse> loginSubject = loginMap.get(login);
        if (loginSubject == null) {
            loginSubject = AsyncSubject.create();
            loginService.logIn(request)
                    .flatMap(new Func1<LoginResponse, Observable<LoginResponse>>() {
                        @Override
                        public Observable<LoginResponse> call(LoginResponse loginResponse) {
                            if (loginResponse.getToken() != null) {
                                mutableIdentityStore.setEmailAndToken(login.getEmail(), loginResponse.getToken());
                                return Observable.just(loginResponse);
                            } else {
                                return Observable.error(new ApiValidationException("The response did not contain a valid API token"));
                            }
                        }
                    })
                    .flatMap(new Func1<LoginResponse, Observable<LoginResponse>>() {
                        @Override
                        public Observable<LoginResponse> call(final LoginResponse loginResponse) {
                            return organizationManager.getOrganizations()
                                    .flatMap(new Func1<OrganizationsResponse, Observable<LoginResponse>>() {
                                        @Override
                                        public Observable<LoginResponse> call(OrganizationsResponse response) {
                                            return Observable.just(loginResponse);
                                        }
                                    });
                        }
                    })
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Logger.error(this, "Failed to complete the log-in request", throwable);
                            analytics.record(Events.Identity.UserLoginFailure);
                        }
                    })
                    .doOnCompleted(new Action0() {
                        @Override
                        public void call() {
                            Logger.info(this, "Successfully completed the log-in request");
                            isLoggedInBehaviorSubject.onNext(true);
                            analytics.record(Events.Identity.UserLoginSuccess);
                        }
                    })
                    .subscribe(loginSubject);
            loginMap.put(login, loginSubject);
        }
        return loginSubject;
    }

    public synchronized void markLoginComplete(@NonNull final LoginParams login) {
        loginMap.remove(login);
    }

    public synchronized Observable<LogoutResponse> logOut() {
        Logger.info(this, "Initiating user log-out");
        this.analytics.record(Events.Identity.UserLogout);

        final LogoutService logoutService = serviceManager.getService(LogoutService.class);

        if (logoutSubject == null) {
            logoutSubject = BehaviorSubject.create();
            logoutService.logOut()
                    .doOnNext(new Action1<LogoutResponse>() {
                        @Override
                        public void call(LogoutResponse logoutResponse) {
                            mutableIdentityStore.setEmailAndToken(null, null);
                        }
                    })
                    .doOnError(new Action1<Throwable>() {
                        @Override
                        public void call(Throwable throwable) {
                            Logger.error(this, "Failed to complete the log-out request", throwable);
                            analytics.record(Events.Identity.UserLogoutFailure);
                        }
                    })
                    .doOnCompleted(new Action0() {
                        @Override
                        public void call() {
                            Logger.info(this, "Successfully completed the log-out request");
                            isLoggedInBehaviorSubject.onNext(false);
                            analytics.record(Events.Identity.UserLogoutSuccess);
                        }
                    })
                    .subscribe(logoutSubject);
        }
        return logoutSubject;
    }

    public synchronized void markLogoutComplete() {
        logoutSubject = null;
    }

    @NonNull
    public Observable<MeResponse> getMe() {
        if (isLoggedIn()) {
            return serviceManager.getService(MeService.class).me();
        } else {
            return Observable.error(new IllegalStateException("Cannot fetch the user's account until we're logged in"));
        }
    }

    @NonNull
    public Observable<MeResponse> updateMe(@NonNull UpdatePushTokensRequest request) {
        if (isLoggedIn()) {
            return serviceManager.getService(MeService.class).me(request);
        } else {
            return Observable.error(new IllegalStateException("Cannot fetch the user's account until we're logged in"));
        }
    }

    @NonNull
    public Observable<OrganizationsResponse> getOrganizations() {
        return organizationManager.getOrganizations();
    }
}
