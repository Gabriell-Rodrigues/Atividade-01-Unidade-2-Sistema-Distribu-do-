package transferencia;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServidorPool {

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.out.println("uso: ServidorPool <porta> <arquivo> <maximo de clientes ao mesmo tempo>");
            return;
        }
        int porta = Integer.parseInt(args[0]);
        Path arquivo = Path.of(args[1]);
        int maximo = Integer.parseInt(args[2]);

        // com todas as threads ocupadas, as conexões novas esperam na fila do pool
        ExecutorService pool = Executors.newFixedThreadPool(maximo);
        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("servidor com pool de " + maximo + " threads na porta " + porta + ", arquivo " + arquivo);
            while (true) {
                Socket cliente = servidor.accept();
                pool.execute(() -> Transferencia.atender(cliente, arquivo));
            }
        }
    }
}
