package com.WWU.CyberEnvironment.BLE.authentication;

import android.app.DialogFragment;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import com.WWU.CyberEnvironment.BLE.DeviceScanActivity;
import com.WWU.CyberEnvironment.BLE.R;
import com.WWU.CyberEnvironment.BLE.repository.Repository;
import com.WWU.CyberEnvironment.BLE.repository.models.AuthenticatedUserDto;

public class UserRegistrationFragment extends DialogFragment {
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_account_registration, container);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setCancelable(true);

        userEmail = view.findViewById(R.id.input_email);
        userPassword = view.findViewById(R.id.input_password);
        userPassword2 = view.findViewById(R.id.input_confirm_password);
        registerButton = view.findViewById(R.id.button_register_account);

        prepareDialog();
    }

    @Override
    public void onResume() {
        super.onResume();

        Window window = getDialog().getWindow();

        if (window == null) {
            dismiss();
            return;
        }

        window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT
        );

        window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
    }

    private void prepareDialog() {
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String tmpEmail = userEmail.getText().toString();
                final String pass1 = userPassword.getText().toString();
                final String pass2 = userPassword2.getText().toString();

                boolean cancel = false;
                View focusView = null;

                if (!TextUtils.isEmpty(pass1) && !isPasswordValid(pass1)) {
                    userPassword.setError(getString(R.string.error_invalid_password));
                    focusView = userPassword;
                    cancel = true;
                }

                if (!pass1.equals(pass2)) {
                    userPassword.setError(getString(R.string.Passwords_dont_match));
                    focusView = userPassword;
                    cancel = true;
                }

                if (TextUtils.isEmpty(tmpEmail)) {
                    userEmail.setError(getString(R.string.error_field_required));
                    focusView = userEmail;
                    cancel = true;
                } else if (!isEmailValid(tmpEmail)) {
                    userEmail.setError(getString(R.string.error_invalid_email));
                    focusView = userEmail;
                    cancel = true;
                }

                if (cancel) {
                    focusView.requestFocus();
                } else {
                    new Thread(new Runnable() {
                        public void run() {
                            AuthenticatedUserDto response = repository.userRegister(tmpEmail, pass1, pass2);
                            validateResponse(response);
                        }
                    }).start();
                }
            }
        });
    }

    private void validateResponse(AuthenticatedUserDto response) {
        if(response == null) {
            registrationError(getString(R.string.error_registration));
            return;
        } else if (response.error != null) {
            registrationError(response.error);
            return;
        }

        CurrentUser.key = response.key;
        CurrentUser.id = response.user;

        final Intent intent = new Intent(getActivity(), DeviceScanActivity.class);

        startActivity(intent);
    }

    private void registrationError(final String message) {
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                userEmail.setError(message);
                userEmail.requestFocus();
            }
        });
    }

    private boolean isEmailValid(String email) {
        return email.contains("@");
    }

    private boolean isPasswordValid(String password) {
        return password.length() > 7;
    }

    private EditText userEmail;
    private EditText userPassword;
    private EditText userPassword2;
    private Button registerButton;

    private Repository repository = new Repository();
}


