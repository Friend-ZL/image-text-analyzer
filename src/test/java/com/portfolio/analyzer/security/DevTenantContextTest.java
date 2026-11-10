package com.portfolio.analyzer.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DevTenantContextTest {
    
    private DevTenantContext devTenantContext;
    private MockHttpServletRequest request;
    
    @BeforeEach
    void setUp() {
        devTenantContext = new DevTenantContext();
        request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    
    @Test
    void shouldExtractTenantAndUserFromHeaders() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        
        TenantPrincipal principal = devTenantContext.current();
        
        assertEquals("tenant1", principal.tenantId());
        assertEquals("user1", principal.userId());
        assertTrue(principal.roles().isEmpty());
        assertTrue(principal.groups().isEmpty());
    }
    
    @Test
    void shouldExtractRolesAndGroups() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-Roles", "admin,user");
        request.addHeader("X-Groups", "group1,group2");
        
        TenantPrincipal principal = devTenantContext.current();
        
        assertEquals(Set.of("admin", "user"), principal.roles());
        assertEquals(Set.of("group1", "group2"), principal.groups());
    }
    
    @Test
    void shouldHandleEmptyRolesAndGroups() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-Roles", "");
        request.addHeader("X-Groups", "");
        
        TenantPrincipal principal = devTenantContext.current();
        
        assertTrue(principal.roles().isEmpty());
        assertTrue(principal.groups().isEmpty());
    }
    
    @Test
    void shouldThrowExceptionForMissingTenantId() {
        request.addHeader("X-User-Id", "user1");
        
        TenantMissingException exception = assertThrows(
            TenantMissingException.class,
            () -> devTenantContext.current()
        );
        
        assertTrue(exception.getMessage().contains("X-Tenant-Id"));
    }
    
    @Test
    void shouldThrowExceptionForMissingUserId() {
        request.addHeader("X-Tenant-Id", "tenant1");
        
        TenantMissingException exception = assertThrows(
            TenantMissingException.class,
            () -> devTenantContext.current()
        );
        
        assertTrue(exception.getMessage().contains("X-User-Id"));
    }
    
    @Test
    void shouldGetTenantIdDirectly() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        
        assertEquals("tenant1", devTenantContext.getTenantId());
    }
    
    @Test
    void shouldGetUserIdDirectly() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        
        assertEquals("user1", devTenantContext.getUserId());
    }
    
    @Test
    void shouldGetRolesDirectly() {
        request.addHeader("X-Tenant-Id", "tenant1");
        request.addHeader("X-User-Id", "user1");
        request.addHeader("X-Roles", "admin,user");
        
        assertEquals(Set.of("admin", "user"), devTenantContext.getRoles());
    }
}
