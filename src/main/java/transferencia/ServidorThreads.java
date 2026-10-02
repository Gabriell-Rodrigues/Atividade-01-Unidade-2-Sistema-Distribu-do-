package transferencia;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;

public class ServidorThreads {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("uso: ServidorThreads <porta> <arquivo>");
            return;
        }
        int porta = Integer.parseInt(args[0]);
        Path arquivo = Path.of(args[1]);

        try (ServerSocket servidor = new ServerSocket(porta)) {
            System.out.println("servidor com uma thread por cliente na porta " + porta + ", arquivo " + arquivo);
            while (true) {
                Socket cliente = servidor.accept();
                // uma thread por conexão: todos os clientes são atendidos ao mesmo tempo
                new Thread(() -> Transferencia.atender(cliente, arquivo)).start();
            }
        }
    }
}
