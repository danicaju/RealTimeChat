package com.danicaju.chat.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas del Motor de Permisos Bitfield")
class PermissionTest {

    @Test
    @DisplayName("Evaluación de máscaras de bits por defecto por rol")
    void testDefaultRoleBitmasks() {
        long readOnly = Permission.getDefaultPermissionsForRole(Permission.ROLE_READ_ONLY);
        assertTrue(Permission.hasPermission(readOnly, Permission.VIEW_CHANNEL));
        assertFalse(Permission.hasPermission(readOnly, Permission.SEND_MESSAGES));
        assertFalse(Permission.hasPermission(readOnly, Permission.MANAGE_MESSAGES));

        long member = Permission.getDefaultPermissionsForRole(Permission.ROLE_MEMBER);
        assertTrue(Permission.hasPermission(member, Permission.VIEW_CHANNEL));
        assertTrue(Permission.hasPermission(member, Permission.SEND_MESSAGES));
        assertTrue(Permission.hasPermission(member, Permission.CREATE_THREADS));
        assertFalse(Permission.hasPermission(member, Permission.MANAGE_CHANNELS));

        long admin = Permission.getDefaultPermissionsForRole(Permission.ROLE_ADMIN);
        assertTrue(Permission.hasPermission(admin, Permission.ADMINISTRATOR));
        assertTrue(Permission.hasPermission(admin, Permission.MANAGE_CHANNELS));
        assertTrue(Permission.hasPermission(admin, Permission.MANAGE_ROLES));
    }

    @Test
    @DisplayName("Overrides de permitir y denegar por canal")
    void testChannelOverrides() {
        long member = Permission.DEFAULT_MEMBER;
        // Canal de solo lectura para miembros (deniega SEND_MESSAGES)
        long effective = Permission.computeEffectivePermissions(member, 0L, Permission.SEND_MESSAGES);
        assertTrue(Permission.hasPermission(effective, Permission.VIEW_CHANNEL));
        assertFalse(Permission.hasPermission(effective, Permission.SEND_MESSAGES));

        // Override allow concede MANAGE_MESSAGES puntualmente en este canal
        long elevated = Permission.computeEffectivePermissions(member, Permission.MANAGE_MESSAGES, 0L);
        assertTrue(Permission.hasPermission(elevated, Permission.MANAGE_MESSAGES));

        // ADMINISTRATOR ignora denegaciones de canal
        long adminEffective = Permission.computeEffectivePermissions(Permission.DEFAULT_ADMIN, 0L, Permission.SEND_MESSAGES | Permission.VIEW_CHANNEL);
        assertTrue(Permission.hasPermission(adminEffective, Permission.SEND_MESSAGES));
        assertTrue(Permission.hasPermission(adminEffective, Permission.VIEW_CHANNEL));
    }
}
