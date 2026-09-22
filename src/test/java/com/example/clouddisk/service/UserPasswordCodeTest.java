package com.example.clouddisk.service;

import com.example.clouddisk.entity.User;
import com.example.clouddisk.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserPasswordCodeTest {
    @Test
    void codeOnlyGoesToBoundEmailAndCannotBeReused() {
        UserService service = new UserService();
        UserMapper users = mock(UserMapper.class);
        MailService mail = mock(MailService.class);
        ReflectionTestUtils.setField(service, "userMapper", users);
        ReflectionTestUtils.setField(service, "mailService", mail);
        User user = new User(); user.setId(9L); user.setEmail("bound@example.test");
        when(users.findById(9L)).thenReturn(user);
        when(mail.sendSimpleMail(eq("bound@example.test"), anyString(), anyString())).thenReturn(true);
        when(users.updatePassword(eq(9L), anyString(), anyString())).thenReturn(1);

        assertTrue(service.sendPasswordCode(9L).contains("已发送"));
        assertEquals("请在60秒后重试", service.sendPasswordCode(9L));
        var text = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mail).sendSimpleMail(eq("bound@example.test"), anyString(), text.capture());
        String code = text.getValue().replaceAll(".*?([0-9]{6}).*", "$1");
        assertFalse(service.changePassword(9L, "000000".equals(code) ? "111111" : "000000", "new-pass"));
        assertTrue(service.changePassword(9L, code, "new-pass"));
        assertFalse(service.changePassword(9L, code, "another-pass"));
        verify(users, times(1)).updatePassword(eq(9L), anyString(), anyString());
    }
}
