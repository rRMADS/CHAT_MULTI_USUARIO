package service;

import static protocol.Evento.Tipo.*;

import model.Anexo;

import protocol.Evento;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Dados limitados em memória; a mensagem e o arquivo só são publicados após o commit. */
final class TransferenciasServidor implements AutoCloseable {
    private static final class Upload {
        final String transferencia;
        final Anexo anexo;
        final ChatService sala;
        final long versao;
        final ByteArrayOutputStream dados = new ByteArrayOutputStream();
        int sequencia;
        long ultimo = System.nanoTime();

        Upload(String transferencia, Anexo anexo, ChatService sala) {
            this.transferencia = transferencia;
            this.anexo = anexo;
            this.sala = sala;
            this.versao = sala.versaoHistorico();
        }
    }

    private final Map<ConexaoCliente, Upload> uploads = new IdentityHashMap<>();
    private final ConcurrentMap<ConexaoCliente, AtomicBoolean> downloads =
            new ConcurrentHashMap<>();
    private final ExecutorService executor =
            Executors.newFixedThreadPool(
                    2,
                    r -> {
                        Thread t = new Thread(r, "orkut-arquivos");
                        t.setDaemon(true);
                        return t;
                    });

    void processar(ConexaoCliente c, ChatService sala, Evento e) {
        long agora = System.nanoTime();
        uploads.values().removeIf(u -> agora - u.ultimo > TimeUnit.MINUTES.toNanos(2));
        switch (e.tipo()) {
            case ARQUIVO_INICIO -> {
                if (e.campos().size() != 5)
                    throw new IllegalArgumentException("Dados do arquivo inválidos.");
                UUID.fromString(e.campo(0));
                if (uploads.containsKey(c))
                    throw new IllegalArgumentException("Cancele ou conclua o arquivo anterior.");
                if (uploads.size() >= 4)
                    throw new IllegalArgumentException(
                            "Há outros arquivos sendo enviados. Tente em instantes.");
                var a =
                        new Anexo(
                                UUID.randomUUID().toString(),
                                e.campo(1),
                                Long.parseLong(e.campo(2)),
                                e.campo(3));
                if (!e.campo(4).equals("1"))
                    throw new IllegalArgumentException("Atualize o cliente para enviar arquivos.");
                uploads.put(c, new Upload(e.campo(0), a, sala));
                c.enviar(Evento.de(ARQUIVO_PROGRESSO, e.campo(0), "0", Long.toString(a.tamanho())));
            }
            case ARQUIVO_PARTE -> {
                Upload u = exigir(c, sala, e.campo(0));
                try {
                    if (e.campos().size() != 3 || Integer.parseInt(e.campo(1)) != u.sequencia)
                        throw new IllegalArgumentException("Blocos de arquivo fora de ordem.");
                    byte[] bloco = Base64.getDecoder().decode(e.campo(2));
                    if (bloco.length == 0
                            || bloco.length > 768
                            || u.dados.size() + bloco.length > u.anexo.tamanho())
                        throw new IllegalArgumentException("Tamanho de arquivo inválido.");
                    u.dados.writeBytes(bloco);
                    u.sequencia++;
                    u.ultimo = agora;
                    if (u.sequencia % 32 == 0)
                        c.enviar(
                                Evento.de(
                                        ARQUIVO_PROGRESSO,
                                        u.transferencia,
                                        Integer.toString(u.dados.size()),
                                        Long.toString(u.anexo.tamanho())));
                } catch (RuntimeException falha) {
                    uploads.remove(c);
                    throw falha;
                }
            }
            case ARQUIVO_FIM -> {
                Upload u = exigir(c, sala, e.campo(0));
                uploads.remove(c);
                if (u.versao != sala.versaoHistorico())
                    throw new IllegalArgumentException(
                            "O histórico foi limpo durante o envio. Selecione o arquivo novamente.");
                if (u.dados.size() != u.anexo.tamanho())
                    throw new IllegalArgumentException("Arquivo incompleto. Envie novamente.");
                var m = sala.enviarAnexo(c, u.anexo, u.dados.toByteArray());
                c.enviar(Evento.de(ARQUIVO_PRONTO, u.transferencia, m.id().toString()));
            }
            case ARQUIVO_CANCELAR -> {
                cancelar(c);
            }
            case ARQUIVO_PEDIR -> baixar(c, sala, e.campo(0));
            default -> throw new IllegalArgumentException("Transferência inválida.");
        }
    }

    private Upload exigir(ConexaoCliente c, ChatService sala, String id) {
        Upload u = uploads.get(c);
        if (u == null || u.sala != sala || !u.transferencia.equals(id))
            throw new IllegalArgumentException(
                    "Transferência encerrada. Selecione o arquivo novamente.");
        return u;
    }

    private void baixar(ConexaoCliente c, ChatService sala, String id) {
        UUID.fromString(id);
        if (downloads.containsKey(c))
            throw new IllegalArgumentException("Aguarde o download atual.");
        if (downloads.size() >= 2)
            throw new IllegalArgumentException("Downloads ocupados. Tente em instantes.");
        byte[] dados = sala.lerAnexo(c, id);
        var cancelar = new AtomicBoolean();
        downloads.put(c, cancelar);
        executor.execute(() -> {
            try {
                c.enviarComEspera(Evento.de(DOWNLOAD_INICIO, id, Integer.toString(dados.length)));
                for (int i = 0, n = 0; i < dados.length && !cancelar.get(); i += 3072, n++)
                    c.enviarComEspera(
                            Evento.de(
                                    DOWNLOAD_PARTE,
                                    id,
                                    Integer.toString(n),
                                    Base64.getEncoder()
                                            .encodeToString(
                                                    Arrays.copyOfRange(
                                                            dados,
                                                            i,
                                                            Math.min(dados.length, i + 3072)))));
                if (!cancelar.get()) c.enviarComEspera(Evento.de(DOWNLOAD_FIM, id));
            } catch (RuntimeException falha) {
                try {
                    c.enviar(
                            Evento.de(ARQUIVO_ERRO, id, "Download interrompido. Tente novamente."));
                } catch (RuntimeException ignored) {
                }
            } finally {
                downloads.remove(c, cancelar);
            }
        });
    }

    void cancelar(ConexaoCliente c) {
        uploads.remove(c);
        var atual = downloads.remove(c);
        if (atual != null) atual.set(true);
    }

    @Override
    public void close() {
        uploads.clear();
        downloads.values().forEach(c -> c.set(true));
        downloads.clear();
        executor.shutdownNow();
    }
}
