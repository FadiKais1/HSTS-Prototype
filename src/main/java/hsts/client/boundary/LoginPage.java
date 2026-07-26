package hsts.client.boundary;

import hsts.client.net.Client;
import hsts.server.entity.User;

public class LoginPage {
    private Client client;
    private String emailInput;
    private String passwordInput;
    private String errorMessage;

    public void submitLogin(String email, String password) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void showLoginError(String message) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void redirectUser(User user) {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }

    public void clearFields() {
        throw new UnsupportedOperationException(
                "Not implemented in Assignment 2 skeleton"
        );
    }
}
