package com.travelplan.users;

import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserStoreIdentityTest {
    @Test void persistentUserCreateBindsTimestamp() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        @SuppressWarnings("unchecked") ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked") ObjectProvider<PlatformTransactionManager> managers = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(jdbc);
        when(managers.getIfAvailable()).thenReturn(mock(PlatformTransactionManager.class));
        UserStore store = new UserStore(mock(RevocationClient.class), mock(AuthProvisionClient.class), provider, managers);

        var user = store.create("person@example.com", "First", "Last", UserDomain.Role.OPERATOR);

        verify(jdbc).update(startsWith("insert into users"), eq(user.id()), eq(user.email()),
                eq("First"), eq("Last"), eq("OPERATOR"), eq("ACTIVE"),
                eq(Timestamp.from(user.createdAt())));
    }

    @Test void userChangesSynchronizeIdentityBeforeChangingProfile() {
        RevocationClient revocations = mock(RevocationClient.class);
        AuthProvisionClient provisioning = mock(AuthProvisionClient.class);
        @SuppressWarnings("unchecked") ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        UserStore store = new UserStore(revocations, provisioning, provider);
        var user = store.create("person@example.com", "First", "Last", UserDomain.Role.OPERATOR);
        verify(provisioning).provision(eq(user.id()), eq(user.email()), anyString(), eq("OPERATOR"));

        var updated = store.update(user.id(), "new@example.com", null, null, UserDomain.Role.ADMIN, null);
        verify(provisioning).update(user.id(), "new@example.com", "ADMIN", true);
        assertThat(updated.email()).isEqualTo("new@example.com");
        assertThat(updated.role()).isEqualTo(UserDomain.Role.ADMIN);
    }

    @Test void failedIdentityUpdateDoesNotChangeUserProfile() {
        AuthProvisionClient provisioning = mock(AuthProvisionClient.class);
        @SuppressWarnings("unchecked") ObjectProvider<JdbcTemplate> provider = mock(ObjectProvider.class);
        UserStore store = new UserStore(mock(RevocationClient.class), provisioning, provider);
        var user = store.create("person@example.com", "First", "Last", UserDomain.Role.OPERATOR);
        doThrow(new IllegalStateException("auth offline")).when(provisioning)
                .update(user.id(), user.email(), "OPERATOR", false);

        assertThrows(UserStore.Conflict.class, () -> store.update(user.id(), null, null, null, null, UserDomain.Status.DISABLED));
        assertThat(store.get(user.id()).status()).isEqualTo(UserDomain.Status.ACTIVE);
    }
}
