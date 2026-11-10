package com.danicaju.chat.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Pruebas Unitarias del Modelo de Mensajes y Protocolo JSON")
class MessageTest {

    @Test
    @DisplayName("Serialización y deserialización de mensaje JOIN")
    void testJoinMessage() throws IOException {
        Message original = Message.join("alice");
        String json = original.toJson();

        assertNotNull(json);
        assertTrue(json.contains("\"type\":\"JOIN\""));
        assertTrue(json.contains("\"sender\":\"alice\""));

        Message parsed = Message.fromJson(json);
        assertEquals(MessageType.JOIN, parsed.getType());
        assertEquals("alice", parsed.getSender());
        assertTrue(parsed.getTimestamp() > 0);
    }

    @Test
    @DisplayName("Serialización y deserialización de mensaje CHAT público")
    void testChatMessage() throws IOException {
        Message original = Message.chat("bob", "¡Hola a todos!");
        String json = original.toJson();

        Message parsed = Message.fromJson(json);
        assertEquals(MessageType.CHAT, parsed.getType());
        assertEquals("bob", parsed.getSender());
        assertEquals("¡Hola a todos!", parsed.getContent());
        assertEquals(Message.DEFAULT_CHANNEL, parsed.getChannel());
    }

    @Test
    @DisplayName("Serialización y deserialización de mensaje PRIVATE")
    void testPrivateMessage() throws IOException {
        Message original = Message.privateMsg("alice", "bob", "Mensaje confidencial");
        String json = original.toJson();

        Message parsed = Message.fromJson(json);
        assertEquals(MessageType.PRIVATE, parsed.getType());
        assertEquals("alice", parsed.getSender());
        assertEquals("bob", parsed.getTarget());
        assertEquals("Mensaje confidencial", parsed.getContent());
    }

    @Test
    @DisplayName("Serialización y deserialización de mensaje USER_LIST")
    void testUserListMessage() throws IOException {
        List<String> users = List.of("alice", "bob", "charlie");
        Message original = Message.userList(users);
        String json = original.toJson();

        Message parsed = Message.fromJson(json);
        assertEquals(MessageType.USER_LIST, parsed.getType());
        assertNotNull(parsed.getUsers());
        assertEquals(3, parsed.getUsers().size());
        assertTrue(parsed.getUsers().containsAll(users));
    }

    @Test
    @DisplayName("Serialización y deserialización de mensaje SYSTEM y LEAVE")
    void testSystemAndLeaveMessages() throws IOException {
        Message system = Message.system("Servidor reiniciándose");
        Message parsedSystem = Message.fromJson(system.toJson());
        assertEquals(MessageType.SYSTEM, parsedSystem.getType());
        assertEquals("Servidor reiniciándose", parsedSystem.getContent());

        Message leave = Message.leave("alice");
        Message parsedLeave = Message.fromJson(leave.toJson());
        assertEquals(MessageType.LEAVE, parsedLeave.getType());
        assertEquals("alice", parsedLeave.getSender());
    }

    @Test
    @DisplayName("Mensajes de canales y cambio de canal (JOIN_CHANNEL, CHANNEL_LIST)")
    void testChannelMessages() throws IOException {
        Message joinChan = Message.joinChannel("alice", "#ia-computacion");
        Message parsedJoin = Message.fromJson(joinChan.toJson());
        assertEquals(MessageType.JOIN_CHANNEL, parsedJoin.getType());
        assertEquals("alice", parsedJoin.getSender());
        assertEquals("#ia-computacion", parsedJoin.getChannel());

        List<String> channels = List.of("#general", "#ia-computacion", "#random");
        Message chanList = Message.channelList(channels);
        Message parsedList = Message.fromJson(chanList.toJson());
        assertEquals(MessageType.CHANNEL_LIST, parsedList.getType());
        assertEquals(channels, parsedList.getChannels());
    }

    @Test
    @DisplayName("Mensajes de búfer de reproducción e historial (HISTORY)")
    void testHistoryMessage() throws IOException {
        List<Message> history = List.of(
                Message.chat("#ia-computacion", "alice", "Msg 1"),
                Message.chat("#ia-computacion", "bob", "Msg 2")
        );
        Message histMsg = Message.history("#ia-computacion", history);
        Message parsedHist = Message.fromJson(histMsg.toJson());

        assertEquals(MessageType.HISTORY, parsedHist.getType());
        assertEquals("#ia-computacion", parsedHist.getChannel());
        assertEquals(2, parsedHist.getHistory().size());
        assertEquals("Msg 1", parsedHist.getHistory().get(0).getContent());
    }

    @Test
    @DisplayName("Eventos de estado de escritura (TYPING_START, TYPING_STOP)")
    void testTypingEvents() throws IOException {
        Message start = Message.typingStart("alice", "#general");
        Message parsedStart = Message.fromJson(start.toJson());
        assertEquals(MessageType.TYPING_START, parsedStart.getType());
        assertEquals("alice", parsedStart.getSender());
        assertEquals("#general", parsedStart.getChannel());

        Message stop = Message.typingStop("alice", "#general");
        Message parsedStop = Message.fromJson(stop.toJson());
        assertEquals(MessageType.TYPING_STOP, parsedStop.getType());
    }

    @Test
    @DisplayName("Eventos de personalización de perfil (PROFILE_UPDATE, PROFILE_REQUEST, PROFILE_RESPONSE)")
    void testProfileEvents() throws IOException {
        UserProfile profile = new UserProfile("alice", "Bio Alice", "cosmic", "gradient-cyber");
        Message update = Message.profileUpdate("alice", profile);
        Message parsedUpdate = Message.fromJson(update.toJson());

        assertEquals(MessageType.PROFILE_UPDATE, parsedUpdate.getType());
        assertEquals("alice", parsedUpdate.getSender());
        assertEquals(profile, parsedUpdate.getProfile());

        Message req = Message.profileRequest("alice", "bob");
        Message parsedReq = Message.fromJson(req.toJson());
        assertEquals(MessageType.PROFILE_REQUEST, parsedReq.getType());
        assertEquals("bob", parsedReq.getTarget());

        Message resp = Message.profileResponse(profile);
        Message parsedResp = Message.fromJson(resp.toJson());
        assertEquals(MessageType.PROFILE_RESPONSE, parsedResp.getType());
        assertEquals("alice", parsedResp.getProfile().getUsername());
    }

    @Test
    @DisplayName("Deserialización de JSON con campos adicionales desconocidos (Forward Compatibility)")
    void testUnknownProperties() throws IOException {
        String jsonWithExtra = "{\"type\":\"CHAT\",\"sender\":\"dani\",\"content\":\"test\",\"extraField\":12345}";
        Message parsed = Message.fromJson(jsonWithExtra);

        assertEquals(MessageType.CHAT, parsed.getType());
        assertEquals("dani", parsed.getSender());
        assertEquals("test", parsed.getContent());
    }

    @Test
    @DisplayName("Verificación de equals, hashCode y toString en Message")
    void testEqualsAndHashCode() {
        Message m1 = Message.chat("#general", "alice", "hola");
        m1.setTimestamp(1000L);
        Message m2 = Message.chat("#general", "alice", "hola");
        m2.setTimestamp(1000L);

        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());
        assertTrue(m1.toString().contains("alice"));

        Message m3 = Message.chat("#general", "bob", "hola");
        m3.setTimestamp(1000L);
        assertNotEquals(m1, m3);
    }

    @Test
    @DisplayName("Manejo de lista de usuarios nula en Message")
    void testNullUsersHandling() {
        Message msg = new Message();
        assertNotNull(msg.getUsers());
        assertTrue(msg.getUsers().isEmpty());

        msg.setUsers(null);
        assertNotNull(msg.getUsers());
        assertTrue(msg.getUsers().isEmpty());
    }

    @Test
    @DisplayName("Mensaje del sistema con y sin canal")
    void testSystemMessageFactory() {
        Message sysGlobal = Message.system("Aviso global");
        assertEquals(MessageType.SYSTEM, sysGlobal.getType());
        assertEquals("Aviso global", sysGlobal.getContent());
        assertNull(sysGlobal.getChannel());

        Message sysChannel = Message.system("#ia-computacion", "Aviso de canal");
        assertEquals(MessageType.SYSTEM, sysChannel.getType());
        assertEquals("#ia-computacion", sysChannel.getChannel());
        assertEquals("Aviso de canal", sysChannel.getContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("Rechazo de cadenas JSON vacías o nulas")
    void testEmptyJsonRejection(String emptyJson) {
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson(emptyJson));
        assertThrows(IllegalArgumentException.class, () -> Message.fromJson(null));
    }
}
