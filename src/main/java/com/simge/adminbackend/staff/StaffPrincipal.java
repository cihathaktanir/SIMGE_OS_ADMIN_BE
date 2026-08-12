package com.simge.adminbackend.staff;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.simge.adminbackend.appdb.model.StaffUser;

import lombok.Getter;

/**
 * Oturumdaki personel.
 *
 * <p>
 * Oturum veritabanına serileştirildiği için alanlar sade tutulur; parola özeti
 * kimlik doğrulandıktan sonra {@link #eraseCredentials()} ile düşürülür.
 * </p>
 *
 * <p>
 * {@link #mustChangePassword} nesnenin içinde taşınıyor ki her istekte
 * veritabanına gitmeden kapı denetlenebilsin ({@code StaffPasswordChangeGate}).
 * Parola değiştiğinde oturumdaki nesne <b>yenilenmek zorunda</b> — yoksa bayrak
 * eski oturumda açık kalır ve kullanıcı kendi parolasını değiştirdiği hâlde
 * panele giremez.
 * </p>
 */
@Getter
public class StaffPrincipal implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private transient String passwordHash;
    private final String fullName;
    private final boolean active;
    private final boolean mustChangePassword;
    private final Set<String> roles;

    public StaffPrincipal(StaffUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.fullName = user.getFullName();
        this.active = user.isActive();
        this.mustChangePassword = user.isMustChangePassword();
        this.roles = new LinkedHashSet<>(
                user.getRoles() == null ? List.of() : user.getRoles());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true; // kilitleme StaffAuthService'te, deneme sayacıyla yönetiliyor
    }

    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
