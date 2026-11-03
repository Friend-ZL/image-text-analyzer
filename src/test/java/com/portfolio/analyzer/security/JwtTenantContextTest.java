package com.portfolio.analyzer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtTenantContextTest {
    
    private JwtTenantContext jwtTenantContext;
    private JwtClaimProperties claimProperties;
    
    @BeforeEach
    void setUp() {
        claimProperties = new JwtClaimProperties();
        jwtTenantContext = new JwtTenantContext(claimProperties);
        
        SecurityContextHolder.clearContext();
    }
    
    @Test
    void shouldExtractPrincipalFromJwt() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1",
            "tenant_id", "tenant1",
            "roles", List.of("admin", "user"),
            "groups", List.of("group1")
        ));
        
        setupSecurityContext(jwt);
        
        TenantPrincipal principal = jwtTenantContext.current();
        
        assertEquals("tenant1", principal.tenantId());
        assertEquals("user1", principal.userId());
        assertEquals(Set.of("admin", "user"), principal.roles());
        assertEquals(Set.of("group1"), principal.groups());
    }
    
    @Test
    void shouldExtractRolesFromRealmAccess() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1",
            "tenant_id", "tenant1",
            "realm_access", Map.of("roles", List.of("admin", "user"))
        ));
        
        setupSecurityContext(jwt);
        
        TenantPrincipal principal = jwtTenantContext.current();
        
        assertEquals(Set.of("admin", "user"), principal.roles());
    }
    
    @Test
    void shouldMergeRolesFromMultipleClaims() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1",
            "tenant_id", "tenant1",
            "realm_access", Map.of("roles", List.of("admin")),
            "roles", List.of("user")
        ));
        
        setupSecurityContext(jwt);
        
        TenantPrincipal principal = jwtTenantContext.current();
        
        assertEquals(Set.of("admin", "user"), principal.roles());
    }
    
    @Test
    void shouldHandleEmptyRolesAndGroups() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1",
            "tenant_id", "tenant1"
        ));
        
        setupSecurityContext(jwt);
        
        TenantPrincipal principal = jwtTenantContext.current();
        
        assertTrue(principal.roles().isEmpty());
        assertTrue(principal.groups().isEmpty());
    }
    
    @Test
    void shouldThrowExceptionForMissingTenantId() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1"
        ));
        
        setupSecurityContext(jwt);
        
        TenantMissingException exception = assertThrows(
            TenantMissingException.class,
            () -> jwtTenantContext.current()
        );
        
        assertTrue(exception.getMessage().contains("tenant"));
    }
    
    @Test
    void shouldThrowExceptionForMissingSubject() {
        Jwt jwt = createJwt(Map.of(
            "tenant_id", "tenant1"
        ));
        
        setupSecurityContext(jwt);
        
        TenantMissingException exception = assertThrows(
            TenantMissingException.class,
            () -> jwtTenantContext.current()
        );
        
        assertTrue(exception.getMessage().contains("subject"));
    }
    
    @Test
    void shouldThrowExceptionForNoAuthentication() {
        SecurityContextHolder.clearContext();
        
        assertThrows(TenantMissingException.class, 
            () -> jwtTenantContext.current()
        );
    }
    
    @Test
    void shouldThrowExceptionForNonJwtPrincipal() {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn("not-a-jwt");
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
        
        assertThrows(TenantMissingException.class, 
            () -> jwtTenantContext.current()
        );
    }
    
    @Test
    void shouldGetTenantIdDirectly() {
        Jwt jwt = createJwt(Map.of(
            "sub", "user1",
            "tenant_id", "tenant1"
        ));
        
        setupSecurityContext(jwt);
        
        assertEquals("tenant1", jwtTenantContext.getTenantId());
    }
    
    private Jwt createJwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claims(c -> c.putAll(claims))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
    }
    
    private void setupSecurityContext(Jwt jwt) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(jwt);
        
        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);
    }
}
