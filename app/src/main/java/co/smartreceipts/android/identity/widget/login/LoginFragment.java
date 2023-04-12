package co.smartreceipts.android.identity.widget.login;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputLayout;
import com.jakewharton.rxbinding3.view.RxView;
import com.jakewharton.rxbinding3.widget.RxTextView;

import javax.inject.Inject;

import co.smartreceipts.analytics.log.Logger;
import co.smartreceipts.android.R;
import co.smartreceipts.android.activities.NavigationHandler;
import co.smartreceipts.android.databinding.LoginFragmentBinding;
import co.smartreceipts.android.identity.widget.login.model.UiInputValidationIndicator;
import co.smartreceipts.android.utils.SoftKeyboardManager;
import co.smartreceipts.android.widget.model.UiIndicator;
import dagger.android.support.AndroidSupportInjection;
import io.reactivex.Observable;
import kotlin.Unit;

public class LoginFragment extends Fragment implements LoginView {

    @Inject
    LoginPresenter presenter;

    @Inject
    LoginRouter router;

    @Inject
    NavigationHandler navigationHandler;

    public static final String IS_FROM_OCR = "is_from_ocr";

    private TextView loginFieldsHintMessage;
    private EditText emailInput;
    private EditText passwordInput;
    private ProgressBar progress;
    private Button loginButton;
    private Button signUpButton;

    private LoginFragmentBinding binding;

    @NonNull
    public static LoginFragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public void onAttach(Context context) {
        AndroidSupportInjection.inject(this);
        super.onAttach(context);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = LoginFragmentBinding.inflate(inflater, container, false);

        loginFieldsHintMessage = binding.loginFieldsHint;
        emailInput = binding.loginFieldEmail;
        passwordInput = binding.loginFieldPassword;
        progress = binding.progress;
        loginButton = binding.loginButton;
        signUpButton = binding.signUpButton;

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.emailInput.requestFocus();
        SoftKeyboardManager.showKeyboard(this.emailInput);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Toolbar toolbar;
        if (navigationHandler.isDualPane()) {
            toolbar = getActivity().findViewById(R.id.toolbar);
            binding.toolbar.toolbar.setVisibility(View.GONE);
        } else {
            toolbar = binding.toolbar.toolbar;
        }
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        menu.clear();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            return router.navigateBack();
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        Logger.debug(this, "onResume");

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.login_toolbar_title);
            actionBar.setSubtitle("");
        }

        this.presenter.subscribe();
    }

    @Override
    public void onPause() {
        Logger.debug(this, "onPause");
        this.presenter.unsubscribe();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        Logger.debug(this, "onDestroyView");
        SoftKeyboardManager.hideKeyboard(emailInput);
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void present(@NonNull UiIndicator<String> uiIndicator) {
        progress.setVisibility(uiIndicator.getState() == UiIndicator.State.Loading ? View.VISIBLE : View.GONE);
        if (uiIndicator.getState() != UiIndicator.State.Idle) {
            loginButton.setEnabled(uiIndicator.getState() != UiIndicator.State.Loading);
            signUpButton.setEnabled(uiIndicator.getState() != UiIndicator.State.Loading);
        }
        if (uiIndicator.getData().isPresent()) {
            Toast.makeText(getContext(), uiIndicator.getData().get(), Toast.LENGTH_SHORT).show();
        }
        if (uiIndicator.getState() == UiIndicator.State.Success) {
            router.navigateBack();
            if(getArguments().getBoolean(IS_FROM_OCR)) {
                router.navigationHandler.navigateToOcrConfigurationFragment();
            }
        }
    }

    @Override
    public void present(@NonNull UiInputValidationIndicator uiInputValidationIndicator) {
        final boolean enableButtons = uiInputValidationIndicator.isEmailValid() && uiInputValidationIndicator.isPasswordValid();
        loginFieldsHintMessage.setText(uiInputValidationIndicator.getMessage());
        loginButton.setEnabled(enableButtons);
        signUpButton.setEnabled(enableButtons);
        highlightInput(binding.emailWrapper, uiInputValidationIndicator.isEmailValid() || emailInput.getText().toString().isEmpty());
        highlightInput(binding.passwordWrapper, uiInputValidationIndicator.isPasswordValid() || passwordInput.getText().toString().isEmpty());
    }

    @NonNull
    @Override
    public Observable<CharSequence> getEmailTextChanges() {
        return RxTextView.textChanges(emailInput);
    }

    @NonNull
    @Override
    public Observable<CharSequence> getPasswordTextChanges() {
        return RxTextView.textChanges(passwordInput);
    }

    @NonNull
    @Override
    public Observable<Unit> getLoginButtonClicks() {
        return RxView.clicks(loginButton);
    }

    @NonNull
    @Override
    public Observable<Unit> getSignUpButtonClicks() {
        return RxView.clicks(signUpButton);
    }

    private void highlightInput(@NonNull TextInputLayout inputLayout, boolean isValid) {
        inputLayout.setError(isValid ? null : " ");
    }

}