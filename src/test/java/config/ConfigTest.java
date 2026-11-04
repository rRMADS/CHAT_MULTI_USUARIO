package config;

import model.Sala;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {
    @TempDir Path pasta;
    private Config ler(String conteudo) throws Exception {
        Path arquivo = pasta.resolve("chat.properties"); Files.writeString(arquivo, conteudo, StandardCharsets.UTF_8);
        return Config.carregar(arquivo);
    }
    @Test void arquivoAusenteUsaCincoSalasPadrao() throws Exception {
        var config = Config.carregar(pasta.resolve("ausente.properties"));
        assertEquals(5050, config.porta()); assertEquals(Sala.padrao(), config.salas());
    }
    @Test void leNomesAcentuadosEPermiteRenomearGeral() throws Exception {
        var config = ler("server.rooms=geral:Recepção;matematica:Matemática;redes:Redes de Computadores\nserver.port=6060\n");
        assertEquals(6060, config.porta()); assertEquals(3, config.salas().size());
        assertEquals(new Sala("geral", "Recepção"), config.salas().get(0)); assertEquals("Matemática", config.salas().get(1).nome());
    }
    @Test void aceitaArquivoUtf8ComBomEListaComEspacos() throws Exception {
        var config = ler("\uFEFFserver.rooms= GERAL : Entrada ; java : Java e POO ;\n");
        assertEquals(new Sala("geral", "Entrada"), config.salas().get(0)); assertEquals("Java e POO", config.salas().get(1).nome());
    }
    @Test void recusaListaSemGeralOuComIdsRepetidos() {
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=java:Java\n"));
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=geral:Geral;GERAL:Outra\n"));
    }
    @Test void recusaSalaSemSeparadorOuIdInvalidoOuNomeVazio() {
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=geral:Geral;Java\n"));
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=geral:Geral;minha sala:Inválida\n"));
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=geral:   \n"));
        assertThrows(IllegalArgumentException.class, () -> ler("server.rooms=\n"));
    }
    @Test void recusaMaisDeVinteSalas() {
        var lista = new StringBuilder("server.rooms=geral:Geral");
        for (int i = 0; i < 20; i++) lista.append(";s").append(i).append(":Sala ").append(i);
        assertThrows(IllegalArgumentException.class, () -> ler(lista.toString()));
    }
    @Test void listaDeSalasEImutavel() throws Exception {
        var config = ler("server.rooms=geral:Geral;java:Java\n");
        assertThrows(UnsupportedOperationException.class, () -> config.salas().clear());
    }
}
