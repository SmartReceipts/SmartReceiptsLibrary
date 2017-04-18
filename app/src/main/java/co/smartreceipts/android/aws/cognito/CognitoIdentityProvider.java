package co.smartreceipts.android.aws.cognito;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import com.google.common.base.Preconditions;
import com.hadisatrio.optional.Optional;

import co.smartreceipts.android.identity.IdentityManager;
import co.smartreceipts.android.identity.apis.me.Cognito;
import co.smartreceipts.android.identity.apis.me.User;
import co.smartreceipts.android.utils.log.Logger;
import io.reactivex.Single;


class CognitoIdentityProvider {

    private final IdentityManager identityManager;
    private final LocalCognitoTokenStore localCognitoTokenStore;

    public CognitoIdentityProvider(@NonNull IdentityManager identityManager, @NonNull Context context) {
        this(identityManager, new LocalCognitoTokenStore(context));
    }

    @VisibleForTesting
    CognitoIdentityProvider(@NonNull IdentityManager identityManager, @NonNull LocalCognitoTokenStore localCognitoTokenStore) {
        this.identityManager = Preconditions.checkNotNull(identityManager);
        this.localCognitoTokenStore = Preconditions.checkNotNull(localCognitoTokenStore);
    }

    @NonNull
    public Single<Optional<Cognito>> prefetchCognitoTokenIfNeeded() {
        return Single.fromCallable(this::getCachedCognitoToken)
                .flatMap(cognitoOptional -> {
                    if (!cognitoOptional.isPresent()
                            || cognitoOptional.get().getCognitoToken() == null
                            || cognitoOptional.get().getIdentityId() == null) {
                        Logger.debug(CognitoIdentityProvider.this, "Existing cognito token is invalid. Pre-fetching...");
                        return refreshCognitoToken();
                    } else {
                        Logger.debug(CognitoIdentityProvider.this, "Existing cognito token is valid");
                        return Single.just(cognitoOptional);
                    }
                });
    }

    @NonNull
    public Single<Optional<Cognito>> refreshCognitoToken() {
        return this.identityManager.getMe()
                .doOnSubscribe(disposable -> {
                    Logger.debug(CognitoIdentityProvider.this, "Clearing the cached cognito token to refresh");
                    localCognitoTokenStore.persist(null);
                })
                .<Optional<Cognito>>map(meResponse -> {
                    if (meResponse != null && meResponse.getUser() != null) {
                        Logger.debug(CognitoIdentityProvider.this, "Retrieve a valid token response");
                        final User user = meResponse.getUser();
                        return Optional.of(new Cognito(user.getCognitoToken(), user.getIdentityId(), user.getCognitoTokenExpiresAt()));
                    } else {
                        Logger.warn(CognitoIdentityProvider.this, "Failed to fetch a valid token");
                        return Optional.absent();
                    }
                })
                .doOnNext(optionalCognito -> {
                    if (optionalCognito.isPresent()) {
                        localCognitoTokenStore.persist(optionalCognito.get());
                    }else {
                        localCognitoTokenStore.persist(null);
                    }
                })
                .singleOrError();
    }

    @Nullable
    public Cognito synchronouslyRefreshCognitoToken() {
        try {
            return refreshCognitoToken()
                    .onErrorReturn(throwable -> Optional.absent())
                    .blockingGet().get();
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    public Optional<Cognito> getCachedCognitoToken() {
        Cognito cognitoToken = localCognitoTokenStore.getCognitoToken();
        return cognitoToken != null ? Optional.of(cognitoToken) : Optional.absent();
    }
}
