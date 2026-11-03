package com.portfolio.analyzer.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TenantPrincipalTest {
    
    @Test
    void shouldCreatePrincipalWithAllFields() {
        Set<String> roles = Set.of("admin", "user");
        Set<String> groups = Set.of("group1", "group2");
        
        TenantPrincipal principal = new TenantPrincipal(
            "tenant1", "user1", roles, groups
        );
        
        assertEquals("tenant1", principal.tenantId());
        assertEquals("user1", principal.userId());
        assertEquals(roles, principal.roles());
        assertEquals(groups, principal.groups());
    }
    
    @Test
    void shouldCreatePrincipalWithoutRolesAndGroups() {
        TenantPrincipal principal = TenantPrincipal.of("tenant1", "user1");
        
        assertEquals("tenant1", principal.tenantId());
        assertEquals("user1", principal.userId());
        assertTrue(principal.roles().isEmpty());
        assertTrue(principal.groups().isEmpty());
    }
    
    @Test
    void shouldReturnImmutableCollections() {
        Set<String> roles = new HashSet<>();
        roles.add("admin");
        
        TenantPrincipal principal = new TenantPrincipal(
            "tenant1", "user1", roles, Set.of()
        );
        
        assertThrows(UnsupportedOperationException.class, () -> 
            principal.roles().add("hacker")
        );
    }
    
    @Test
    void shouldDefensiveCopyInputCollections() {
        Set<String> roles = new HashSet<>();
        roles.add("admin");
        
        TenantPrincipal principal = new TenantPrincipal(
            "tenant1", "user1", roles, Set.of()
        );
        
        roles.add("hacker");
        
        assertEquals(1, principal.roles().size());
        assertTrue(principal.roles().contains("admin"));
        assertFalse(principal.roles().contains("hacker"));
    }
    
    @Test
    void shouldHandleNullCollections() {
        TenantPrincipal principal = new TenantPrincipal(
            "tenant1", "user1", null, null
        );
        
        assertNotNull(principal.roles());
        assertNotNull(principal.groups());
        assertTrue(principal.roles().isEmpty());
        assertTrue(principal.groups().isEmpty());
    }
    
    @Test
    void shouldThrowExceptionForNullTenantId() {
        assertThrows(NullPointerException.class, () -> 
            new TenantPrincipal(null, "user1", Set.of(), Set.of())
        );
    }
    
    @Test
    void shouldThrowExceptionForNullUserId() {
        assertThrows(NullPointerException.class, () -> 
            new TenantPrincipal("tenant1", null, Set.of(), Set.of())
        );
    }
}
