package hsts.common;

import java.io.Serializable;

public class LoginRequestPayload implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String email;
    private final String password;

    public LoginRequestPayload(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
}
