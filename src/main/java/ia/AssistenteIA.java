package ia;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Duas consultas simultâneas, no máximo seis aguardando/concluindo. */
public final class AssistenteIA implements AutoCloseable {
    private final ClienteIA cliente;
    private final Semaphore vagas=new Semaphore(6);
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r -> {
        Thread t=new Thread(r,"conexo-ia"); t.setDaemon(true); return t;
    });
    public AssistenteIA(ClienteIA cliente) { this.cliente=cliente; }
    public boolean disponivel() { return cliente!=null && !executor.isShutdown(); }
    public Future<?> perguntar(String pergunta,Runnable aceitar,Consumer<String> sucesso,Consumer<String> erro) {
        if(!disponivel()) throw new IllegalArgumentException("A IA ainda não foi ativada neste servidor. O chat pode ser usado normalmente.");
        if(!vagas.tryAcquire()) throw new IllegalArgumentException("A IA está atendendo outras perguntas. Tente novamente em instantes.");
        try { aceitar.run(); } catch(RuntimeException e) { vagas.release(); throw e; }
        FutureTask<Void> tarefa=new FutureTask<>(() -> {
            try { String resposta=cliente.responder(pergunta); if(!Thread.currentThread().isInterrupted()) sucesso.accept(resposta); }
            catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            catch(Exception e) { if(!Thread.currentThread().isInterrupted()) erro.accept(e instanceof FalhaIA ? e.getMessage() : "Não foi possível consultar a IA. Tente novamente em instantes."); }
            return null;
        }) { @Override protected void done() { vagas.release(); } };
        try { executor.execute(tarefa); } catch(RejectedExecutionException e) { tarefa.cancel(false); throw new IllegalArgumentException("A IA está sendo encerrada."); }
        return tarefa;
    }
    public void cancelar(Future<?> tarefa) { tarefa.cancel(true); if(tarefa instanceof Runnable r) executor.remove(r); }
    @Override public void close() { executor.shutdownNow().forEach(r -> { if(r instanceof Future<?> f) f.cancel(true); }); }
}
