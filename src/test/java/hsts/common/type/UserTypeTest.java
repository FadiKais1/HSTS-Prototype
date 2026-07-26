package hsts.common.type;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

public class UserTypeTest {
    @Test
    public void userRoleHasExactValues() {
        assertArrayEquals(
                new UserRole[]{
                        UserRole.STUDENT,
                        UserRole.TEACHER,
                        UserRole.COORDINATOR,
                        UserRole.PRINCIPAL
                },
                UserRole.values()
        );
    }

    @Test
    public void userStatusHasExactValues() {
        assertArrayEquals(
                new UserStatus[]{UserStatus.ACTIVE, UserStatus.BLOCKED},
                UserStatus.values()
        );
    }
}
