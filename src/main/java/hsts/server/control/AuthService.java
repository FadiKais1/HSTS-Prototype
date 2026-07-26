package hsts.server.control;

import hsts.server.entity.User;

public class AuthService {
    private DatabaseService databaseService;

    public User login(String email, String password) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void logout(int userId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validateCredentials(String email, String password) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public boolean validatePermission(int userId, String action) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }

    public void blockLogin(int userId) {
        throw new UnsupportedOperationException("Not implemented in Assignment 2 skeleton");
    }
}
