package client;

import static protocol.Evento.Tipo.*;

import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import model.Anexo;

import protocol.Evento;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;

final class TransferenciasCliente implements AutoCloseable {
    final VBox painel = new VBox(5);
    private final Label estado = new Label();
    private final ProgressBar progresso = new ProgressBar();
    private final Stage janela;
    private final Temas temas;
    private final Supplier<ConexaoChat> conexao;
    private final BooleanSupplier conectado;
    private final Consumer<String> aviso;
    private final Consumer<Runnable> interfaceGrafica;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "orkut-arquivo-cliente");
                t.setDaemon(true);
                return t;
            });
    private String upload;
    private AtomicBoolean cancelarUpload;
    private Download download;

    private static final class Download {
        final Anexo anexo;
        final Path destino;
        final boolean previa;
        final ByteArrayOutputStream dados = new ByteArrayOutputStream();
        int sequencia;
        boolean iniciado;

        Download(Anexo anexo, Path destino, boolean previa) {
            this.anexo = anexo;
            this.destino = destino;
            this.previa = previa;
        }
    }

    TransferenciasCliente(
            Stage janela,
            Temas temas,
            Supplier<ConexaoChat> conexao,
            BooleanSupplier conectado,
            Consumer<String> aviso,
            Consumer<Runnable> interfaceGrafica) {
        this.janela = janela;
        this.temas = temas;
        this.conexao = conexao;
        this.conectado = conectado;
        this.aviso = aviso;
        this.interfaceGrafica = interfaceGrafica;
        progresso.setMaxWidth(Double.MAX_VALUE);
        estado.setWrapText(true);
        estado.getStyleClass().add("small-muted");
        Button cancelar = new Button("Cancelar transferência");
        cancelar.getStyleClass().add("message-action");
        cancelar.setOnAction(e -> cancelar());
        painel.getChildren().addAll(estado, progresso, cancelar);
        ocultar();
    }

    private boolean livre() {
        if (!conectado.getAsBoolean()) {
            aviso.accept("Aguarde a conexão para transferir arquivos.");
            return false;
        }
        if (upload != null || download != null) {
            aviso.accept("Conclua ou cancele a transferência atual.");
            return false;
        }
        return true;
    }

    void enviar() {
        if (!livre()) return;
        FileChooser seletor = new FileChooser();
        seletor.setTitle("Enviar arquivo à sala · até 5 MB");
        seletor.getExtensionFilters()
                .add(
                        new FileChooser.ExtensionFilter(
                                "Imagens e materiais",
                                "*.png",
                                "*.jpg",
                                "*.jpeg",
                                "*.gif",
                                "*.pdf",
                                "*.txt",
                                "*.csv",
                                "*.java",
                                "*.docx",
                                "*.xlsx",
                                "*.pptx",
                                "*.zip"));
        File arquivo = seletor.showOpenDialog(janela);
        if (arquivo == null) return;
        try {
            Path caminho = arquivo.toPath();
            Anexo a =
                    new Anexo(
                            UUID.randomUUID().toString(),
                            arquivo.getName(),
                            Files.size(caminho),
                            Anexo.tipoDe(arquivo.getName()));
            String id = UUID.randomUUID().toString();
            upload = id;
            cancelarUpload = new AtomicBoolean();
            AtomicBoolean parar = cancelarUpload;
            ConexaoChat canal = conexao.get();
            mostrar("Enviando " + a.nome(), 0);
            executor.execute(() -> {
                try (var entrada = Files.newInputStream(caminho)) {
                    enviar(
                            canal,
                            Evento.de(
                                    ARQUIVO_INICIO,
                                    id,
                                    a.nome(),
                                    Long.toString(a.tamanho()),
                                    a.tipo(),
                                    "1"),
                            parar);
                    byte[] bloco = new byte[768];
                    int n, sequencia = 0;
                    long total = 0;
                    while ((n = entrada.read(bloco)) != -1) {
                        if (total + n > a.tamanho())
                            throw new IOException("O arquivo foi alterado durante o envio.");
                        enviar(
                                canal,
                                Evento.de(
                                        ARQUIVO_PARTE,
                                        id,
                                        Integer.toString(sequencia++),
                                        Base64.getEncoder()
                                                .encodeToString(Arrays.copyOf(bloco, n))),
                                parar);
                        total += n;
                    }
                    if (total != a.tamanho())
                        throw new IOException("O arquivo foi alterado durante o envio.");
                    enviar(canal, Evento.de(ARQUIVO_FIM, id), parar);
                    interfaceGrafica.accept(() -> {
                        if (id.equals(upload))
                            mostrar("Salvando " + a.nome() + " no servidor…", -1);
                    });
                } catch (IOException | RuntimeException e) {
                    interfaceGrafica.accept(() -> {
                        if (id.equals(upload)) {
                            aviso.accept(
                                    parar.get()
                                            ? "Transferência cancelada."
                                            : "Envio interrompido. Selecione o arquivo novamente.");
                            cancelar();
                        }
                    });
                }
            });
        } catch (IOException | IllegalArgumentException e) {
            aviso.accept(e.getMessage());
        }
    }

    private void enviar(ConexaoChat c, Evento evento, AtomicBoolean parar) throws IOException {
        if (parar.get() || c != conexao.get() || !c.enviarAguardando(evento))
            throw new IOException("Conexão interrompida.");
    }

    javafx.scene.Node controles(Anexo anexo) {
        Button salvar = new Button("Baixar · " + anexo.tamanhoLegivel());
        salvar.getStyleClass().add("secondary");
        salvar.setOnAction(e -> baixar(anexo, false));
        FlowPane botoes = new FlowPane(8, 6, salvar);
        if (anexo.imagem()) {
            Button ver = new Button("Ver imagem");
            ver.getStyleClass().add("secondary");
            ver.setOnAction(e -> baixar(anexo, true));
            botoes.getChildren().add(ver);
        }
        return botoes;
    }

    private void baixar(Anexo a, boolean previa) {
        if (!livre()) return;
        Path caminho = null;
        if (!previa) {
            FileChooser seletor = new FileChooser();
            seletor.setTitle("Salvar arquivo");
            seletor.setInitialFileName(a.nome());
            File f = seletor.showSaveDialog(janela);
            if (f == null) return;
            caminho = f.toPath();
        }
        download = new Download(a, caminho, previa);
        mostrar("Baixando " + a.nome(), 0);
        conexao.get().enviarEvento(Evento.de(ARQUIVO_PEDIR, a.id()));
    }

    void receber(Evento e) {
        try {
            String id = e.campo(0);
            switch (e.tipo()) {
                case ARQUIVO_PROGRESSO -> {
                    if (id.equals(upload))
                        mostrar(
                                "Enviando arquivo…",
                                Double.parseDouble(e.campo(1)) / Double.parseDouble(e.campo(2)));
                }
                case ARQUIVO_PRONTO -> {
                    if (id.equals(upload)) {
                        upload = null;
                        cancelarUpload = null;
                        ocultar();
                        aviso.accept("Arquivo publicado na sala e salvo no servidor.");
                    }
                }
                case ARQUIVO_ERRO -> {
                    if (id.equals(upload) || download != null && download.anexo.id().equals(id)) {
                        String mensagem = e.campo(1);
                        cancelar();
                        aviso.accept(mensagem);
                    }
                }
                case DOWNLOAD_INICIO -> {
                    if (download != null && download.anexo.id().equals(id)) {
                        if (Long.parseLong(e.campo(1)) != download.anexo.tamanho()) {
                            cancelar();
                            aviso.accept("Tamanho de arquivo inválido.");
                        } else download.iniciado = true;
                    }
                }
                case DOWNLOAD_PARTE -> {
                    if (download == null || !download.anexo.id().equals(id)) return;
                    byte[] dados = Base64.getDecoder().decode(e.campo(2));
                    if (!download.iniciado
                            || Integer.parseInt(e.campo(1)) != download.sequencia
                            || dados.length > 3072
                            || download.dados.size() + dados.length > download.anexo.tamanho()) {
                        cancelar();
                        aviso.accept("Download incompleto. Tente novamente.");
                        return;
                    }
                    download.sequencia++;
                    download.dados.writeBytes(dados);
                    mostrar(
                            "Baixando " + download.anexo.nome(),
                            download.dados.size() / (double) download.anexo.tamanho());
                }
                case DOWNLOAD_FIM -> {
                    if (download == null || !download.anexo.id().equals(id)) return;
                    Download atual = download;
                    download = null;
                    ocultar();
                    if (!atual.iniciado || atual.dados.size() != atual.anexo.tamanho()) {
                        aviso.accept("Download incompleto. Tente novamente.");
                        return;
                    }
                    byte[] dados = atual.dados.toByteArray();
                    if (atual.previa) prever(atual.anexo, dados);
                    else
                        executor.execute(() -> {
                            try {
                                Files.write(atual.destino, dados);
                                interfaceGrafica.accept(
                                        () -> aviso.accept("Arquivo salvo em " + atual.destino));
                            } catch (IOException erro) {
                                interfaceGrafica.accept(() ->
                                        aviso.accept(
                                                "Não foi possível salvar nesse local. Tente outra pasta."));
                            }
                        });
                }
                default -> {}
            }
        } catch (RuntimeException falha) {
            cancelar();
            aviso.accept("Arquivo inválido ou transferência interrompida. Tente novamente.");
        }
    }

    private void prever(Anexo a, byte[] dados) {
        Dialog<Void> popup = new Dialog<>();
        popup.initOwner(janela);
        popup.setTitle(a.nome());
        popup.setResizable(true);
        var p = popup.getDialogPane();
        temas.registrar(p);
        p.getStylesheets().add(getClass().getResource("/client/chat.css").toExternalForm());
        Image imagem = new Image(new ByteArrayInputStream(dados), 680, 480, true, true);
        if (imagem.isError())
            p.setContent(
                    new Label(
                            "Não foi possível visualizar esta imagem. Você ainda pode baixar o arquivo."));
        else {
            ImageView vista = new ImageView(imagem);
            vista.setPreserveRatio(true);
            vista.setFitWidth(Math.min(680, imagem.getWidth()));
            vista.setFitHeight(Math.min(480, imagem.getHeight()));
            p.setContent(new StackPane(vista));
        }
        p.getButtonTypes().add(ButtonType.CLOSE);
        popup.show();
    }

    void cancelar() {
        String id = upload != null ? upload : download == null ? null : download.anexo.id();
        if (cancelarUpload != null) cancelarUpload.set(true);
        upload = null;
        download = null;
        cancelarUpload = null;
        ocultar();
        ConexaoChat c = conexao.get();
        if (id != null && c != null) c.enviarEvento(Evento.de(ARQUIVO_CANCELAR, id));
    }

    private void mostrar(String texto, double valor) {
        estado.setText(texto);
        progresso.setProgress(valor);
        painel.setVisible(true);
        painel.setManaged(true);
    }

    private void ocultar() {
        painel.setVisible(false);
        painel.setManaged(false);
    }

    @Override
    public void close() {
        cancelar();
        executor.shutdownNow();
    }
}
