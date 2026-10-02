package transferencia;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.util.concurrent.locks.LockSupport;

// limita o upload do nó, já que todos os containers rodam na mesma máquina
public class LimiteBanda {

    static final int BLOCO = 64 * 1024;
    static final long FOLGA_NANOS = 50_000_000L;
    // um limite só para o processo inteiro, dividido entre as threads como a placa de rede
    static final LimiteBanda DO_PROCESSO = criar(System.getenv("BANDA_MBPS"));

    private final double bytesPorSegundo;
    private long proximoLivre = System.nanoTime();

    LimiteBanda(double megabitsPorSegundo) {
        this.bytesPorSegundo = megabitsPorSegundo * 1_000_000 / 8;
    }

    static LimiteBanda criar(String megabits) {
        if (megabits == null || megabits.isBlank() || Double.parseDouble(megabits) <= 0) {
            return null;
        }
        System.out.println("upload limitado a " + megabits + " Mbit/s");
        return new LimiteBanda(Double.parseDouble(megabits));
    }

    public static void escrever(OutputStream saida, byte[] dados, int inicio, int quantidade) throws IOException {
        if (DO_PROCESSO == null) {
            saida.write(dados, inicio, quantidade);
            return;
        }
        int enviados = 0;
        while (enviados < quantidade) {
            int bloco = Math.min(BLOCO, quantidade - enviados);
            DO_PROCESSO.esperar(bloco);
            saida.write(dados, inicio + enviados, bloco);
            enviados += bloco;
        }
    }

    void esperar(int bytes) throws IOException {
        long liberadoEm;
        synchronized (this) {
            long agora = System.nanoTime();
            // reserva o próximo intervalo livre; a folga compensa a thread que acordou atrasada
            proximoLivre = Math.max(proximoLivre, agora - FOLGA_NANOS) + (long) (bytes * 1e9 / bytesPorSegundo);
            liberadoEm = proximoLivre;
        }
        long falta;
        while ((falta = liberadoEm - System.nanoTime()) > 0) {
            LockSupport.parkNanos(falta);
            if (Thread.interrupted()) {
                throw new InterruptedIOException();
            }
        }
    }
}
