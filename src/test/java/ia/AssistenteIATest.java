package ia;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistenteIATest {
    @Test void limitaConsultasERecusaAntesDePublicarNovaPergunta() throws Exception {
        CountDownLatch iniciou=new CountDownLatch(2),liberar=new CountDownLatch(1);
        try(var ia=new AssistenteIA(p->{ iniciou.countDown(); liberar.await(); return "Oi"; })) {
            List<Future<?>> tarefas=new ArrayList<>();
            for(int i=0;i<6;i++) tarefas.add(ia.perguntar("Oi",()->{},r->{},e->{}));
            assertTrue(iniciou.await(2,TimeUnit.SECONDS));
            var aceitou=new java.util.concurrent.atomic.AtomicBoolean();
            assertThrows(IllegalArgumentException.class,()->ia.perguntar("Oi",()->aceitou.set(true),r->{},e->{})); assertFalse(aceitou.get());
            tarefas.forEach(ia::cancelar);
            var nova=ia.perguntar("Oi",()->{},r->{},e->{}); ia.cancelar(nova);
        } finally { liberar.countDown(); }
    }
    @Test void errosInternosNuncaExibemCredenciais() throws Exception {
        CountDownLatch terminou=new CountDownLatch(1); var erro=new java.util.concurrent.atomic.AtomicReference<String>();
        try(var ia=new AssistenteIA(p->{throw new IllegalArgumentException("invalid header: Bearer segredo");})) {
            ia.perguntar("Oi",()->{},r->{},e->{erro.set(e); terminou.countDown();});
            assertTrue(terminou.await(2,TimeUnit.SECONDS)); assertFalse(erro.get().contains("segredo"));
        }
    }
}
