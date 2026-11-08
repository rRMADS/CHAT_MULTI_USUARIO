package model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.*;

class EstadoReacoesTest {
    @Test
    void snapshotCompartilhadoPreservaContagemEPersonalizaSemAlterarOriginal() {
        var pessoas = new HashSet<>(Set.of("Ana", "Bruno"));
        var entrada = new HashMap<String, Set<String>>();
        entrada.put("💜", pessoas);
        var estado = new EstadoReacoes(entrada);
        pessoas.clear();
        entrada.clear();
        assertEquals(List.of(new Reacao("💜", 2, true)), estado.para("Ana"));
        assertEquals(List.of(new Reacao("💜", 2, false)), estado.para("Carla"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> estado.participantes().get("💜").clear());
    }
}
