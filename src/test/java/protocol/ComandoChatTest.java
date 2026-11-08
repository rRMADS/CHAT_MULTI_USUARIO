package protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ComandoChatTest {
    @Test
    void barraSozinhaAbreAjuda() {
        assertEquals("/help", ComandoChat.ler(" / ").orElseThrow().nome());
    }

    @Test
    void reconheceMaiusculasEEspacosCopiados() {
        var comando = ComandoChat.ler("\u00a0 /JOIN\u00a0\u00a0Café\u00a0").orElseThrow();
        assertEquals("/join", comando.nome());
        assertEquals("Café", comando.argumentos());
        assertEquals("/join Café", comando.paraLinha());
    }

    @Test
    void aceitaSeparadorTabNoProtocolo() {
        assertEquals("estudos", ComandoChat.ler("/join\testudos").orElseThrow().argumentos());
    }

    @Test
    void aliasesEmPortuguesSaoCanonicos() {
        assertEquals("/help", ComandoChat.ler("/ajuda").orElseThrow().nome());
        assertEquals("/users", ComandoChat.ler("/usuarios").orElseThrow().nome());
        assertEquals("/history", ComandoChat.ler("/historico 10").orElseThrow().nome());
        assertEquals("/join", ComandoChat.ler("/sala Estudos").orElseThrow().nome());
        assertEquals("/salas", ComandoChat.ler("/rooms").orElseThrow().nome());
        assertEquals("/quit", ComandoChat.ler("/sair").orElseThrow().nome());
    }

    @Test
    void preservaAspasEEspacosNoTextoDaPrivada() {
        var comando = ComandoChat.ler("/msg \"Ana Silva\" Olá,  tudo bem?").orElseThrow();
        assertEquals("\"Ana Silva\" Olá,  tudo bem?", comando.argumentos());
    }

    @Test
    void barraNoMeioDeMensagemNaoViraComando() {
        assertTrue(ComandoChat.ler("Use /help para ver os comandos").isEmpty());
    }
}
